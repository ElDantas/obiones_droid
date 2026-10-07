package io.agentic.functions.tasks;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.core.type.TypeReference;
import io.agentic.core.budget.Budgets;
import io.agentic.functions.config.Params;
import io.agentic.functions.config.Services;
import io.agentic.functions.readiness.ClarityScorer;
import io.agentic.functions.readiness.ReadinessChecker;
import io.agentic.functions.readiness.RepoConfig;
import io.agentic.functions.readiness.RepoConfigLoader;
import io.agentic.functions.readiness.RepoTargetParser;
import io.agentic.functions.store.Run;
import io.agentic.functions.store.RunStore;
import io.agentic.integrations.http.Json;
import io.agentic.integrations.jira.JiraClient;
import io.agentic.integrations.jira.JiraTicket;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ReadinessTask implements RequestHandler<Map<String, Object>, Map<String, Object>> {
    private final JiraClient jira;
    private final Params params;
    private final RunStore store;
    private final ClarityScorer clarity;
    private final RepoConfigLoader configLoader;
    private final ReadinessChecker checker = new ReadinessChecker();
    private final RepoTargetParser parser = new RepoTargetParser();

    public ReadinessTask() {
        this(Services.instance().jira(), Services.instance().params(), Services.instance().runStore(),
                new ClarityScorer(Services.instance().textModel(),
                        () -> Services.instance().params().find("/agentic/bedrock/textModelId").orElse("")),
                new RepoConfigLoader(Services.instance().github()));
    }

    ReadinessTask(JiraClient jira, Params params, RunStore store, ClarityScorer clarity, RepoConfigLoader configLoader) {
        this.jira = jira;
        this.params = params;
        this.store = store;
        this.clarity = clarity;
        this.configLoader = configLoader;
    }

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        String key = TaskSupport.ticketKey(input);
        JiraTicket ticket = jira.getTicket(key);
        RepoTargetParser.Result parsed = parser.parse(ticket.targetRepo());
        if (parsed.repo().isEmpty()) {
            return out("NOT_READY", List.of(parsed.error().orElseThrow()), Budgets.defaults());
        }
        String repo = parsed.repo().get();
        if (!params.isEnabled(repo) && allowlist().contains(repo)) {
            return out("PAUSED", List.of("Agentic runs are paused for " + repo), Budgets.defaults());
        }
        List<String> reasons = checker.check(ticket, allowlist(), params.getInt("/agentic/readiness/maxStoryPoints", 5));
        if (!reasons.isEmpty()) {
            return out("NOT_READY", reasons, Budgets.defaults());
        }
        ClarityScorer.Clarity c = clarity.score(ticket);
        int threshold = params.getInt("/agentic/readiness/clarityThreshold", 70);
        if (c.score() < threshold) {
            List<String> gaps = c.gaps().isEmpty() ? List.of("Clarity score " + c.score() + " is below " + threshold)
                    : c.gaps().stream().map(g -> "Clarify: " + g).toList();
            return out("NOT_READY", gaps, Budgets.defaults());
        }
        Run run = store.get(key).orElseThrow();
        RepoConfig config = configLoader.load(repo, run.budgets());
        store.setRepo(key, repo);
        store.saveBudgets(key, config.budgets());
        return out("READY", List.of(), config.budgets());
    }

    private Set<String> allowlist() {
        try {
            return new HashSet<>(Json.MAPPER.readValue(params.find("/agentic/repos/allowlist").orElse("[]"), new TypeReference<List<String>>() {
            }));
        } catch (Exception e) {
            return Set.of();
        }
    }

    private static Map<String, Object> out(String decision, List<String> reasons, Budgets budgets) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("decision", decision);
        m.put("reasons", reasons);
        m.put("codingTimeoutSeconds", budgets.codingTimeout().toSeconds());
        return m;
    }
}
