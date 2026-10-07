package io.agentic.functions.tasks;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import io.agentic.core.run.Actor;
import io.agentic.core.run.RunState;
import io.agentic.core.scope.ScopeGuard;
import io.agentic.functions.config.Services;
import io.agentic.functions.config.Wiring;
import io.agentic.functions.gates.GateCollector;
import io.agentic.functions.gates.GateEvaluator;
import io.agentic.functions.gates.GateFindings;
import io.agentic.functions.notify.RunTransitions;
import io.agentic.functions.readiness.RepoConfig;
import io.agentic.functions.readiness.RepoConfigLoader;
import io.agentic.functions.store.Run;
import io.agentic.functions.store.RunStore;
import io.agentic.integrations.github.GitHubClient;
import io.agentic.integrations.github.model.PullRequest;

import java.util.List;
import java.util.Map;

public class EvaluateGatesTask implements RequestHandler<Map<String, Object>, Map<String, Object>> {
    private final RunStore store;
    private final RunTransitions transitions;
    private final GitHubClient github;
    private final GateCollector collector;
    private final RepoConfigLoader configLoader;
    private final GateEvaluator evaluator = new GateEvaluator();
    private final ScopeGuard scopeGuard = new ScopeGuard();

    public EvaluateGatesTask() {
        this(Services.instance().runStore(), Wiring.transitions(), Services.instance().github(),
                Wiring.gateCollector(), new RepoConfigLoader(Services.instance().github()));
    }

    EvaluateGatesTask(RunStore store, RunTransitions transitions, GitHubClient github, GateCollector collector, RepoConfigLoader configLoader) {
        this.store = store;
        this.transitions = transitions;
        this.github = github;
        this.collector = collector;
        this.configLoader = configLoader;
    }

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        String key = TaskSupport.ticketKey(input);
        transitions.moveTo(key, RunState.GATES, Actor.BOT, "Checks complete");
        Run run = store.get(key).orElseThrow();
        Integer prNumber = run.prNumber() != null ? run.prNumber() : discoverPr(run);
        if (prNumber == null) {
            List<Integer> open = run.issueNumber() == null ? List.of() : github.openPullRequestsForIssue(run.repo(), run.issueNumber());
            if (open.size() > 1) {
                return TaskSupport.decision("ESCALATE", "Copilot opened more than one PR for this ticket: " + open);
            }
            return evaluator.decide(GateFindings.empty(null), run.humanOverride(), false);
        }
        RepoConfig config = configLoader.load(run.repo(), run.budgets());
        PullRequest pr = github.getPullRequest(run.repo(), prNumber);
        GateFindings findings = collector.collect(run.repo(), prNumber, pr.headSha(), config, run.usage().gateIterations())
                .withScopeViolations(scopeGuard.check(config.budgets(), github.listFiles(run.repo(), prNumber)));
        store.setLastFindings(key, findings);
        return evaluator.decide(findings, run.humanOverride(), true);
    }

    private Integer discoverPr(Run run) {
        if (run.issueNumber() == null) {
            return null;
        }
        List<Integer> open = github.openPullRequestsForIssue(run.repo(), run.issueNumber());
        if (open.size() == 1) {
            store.setPr(run.ticketKey(), open.get(0));
            return open.get(0);
        }
        return null;
    }
}
