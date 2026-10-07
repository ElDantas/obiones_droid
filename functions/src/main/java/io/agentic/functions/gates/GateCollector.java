package io.agentic.functions.gates;

import io.agentic.functions.ingress.Identities;
import io.agentic.functions.readiness.RepoConfig;
import io.agentic.integrations.github.GitHubClient;
import io.agentic.integrations.github.model.Annotation;
import io.agentic.integrations.github.model.CheckRun;
import io.agentic.integrations.github.model.ReviewComment;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class GateCollector {
    public static final String AGENTIC_PREFIX = "agentic/";
    private static final Set<String> FAILED = Set.of("failure", "timed_out", "cancelled", "action_required");

    private final GitHubClient github;
    private final Identities identities;

    public GateCollector(GitHubClient github, Identities identities) {
        this.github = github;
        this.identities = identities;
    }

    public GateFindings collect(String repo, int prNumber, String headSha, RepoConfig config, int gateIteration) {
        List<GateFinding> blocking = new ArrayList<>();
        List<GateFinding> advisory = new ArrayList<>();
        java.util.Map<String, Integer> agentPremium = new java.util.LinkedHashMap<>();
        for (CheckRun run : github.listCheckRuns(repo, headSha)) {
            if (isRequiredCi(run.name(), config) && run.conclusion() != null && FAILED.contains(run.conclusion())) {
                blocking.addAll(ciFindings(repo, run));
            } else {
                collectAgentic(run, blocking);
                if (run.name().startsWith(AGENTIC_PREFIX)) {
                    VerdictParser.parse(run.outputText()).ifPresent(v -> agentPremium.put(Long.toString(run.id()), v.premiumRequests()));
                }
            }
        }
        for (ReviewComment c : github.listReviewComments(repo, prNumber)) {
            if (identities.isCopilotReviewer(c.authorLogin()) && headSha.equals(c.commitId())) {
                GateFinding f = new GateFinding("copilot-review", "copilot-review:" + c.path() + ":" + c.line(), c.path(), c.line(), c.body());
                (gateIteration == 0 ? blocking : advisory).add(f);
            }
        }
        return new GateFindings(headSha, blocking, advisory, List.of(), agentPremium);
    }

    protected void collectAgentic(CheckRun run, List<GateFinding> blocking) {
        if (!run.name().startsWith(AGENTIC_PREFIX) || !"completed".equals(run.status())) {
            return;
        }
        String gate = run.name().substring(AGENTIC_PREFIX.length());
        java.util.Optional<VerdictParser.Verdict> verdict = VerdictParser.parse(run.outputText());
        if (verdict.isEmpty()) {
            blocking.add(new GateFinding(gate, gate + ":invalid-verdict", null, null, gate + ": verdict missing or invalid"));
            return;
        }
        if (verdict.get().pass()) {
            return;
        }
        if (verdict.get().findings().isEmpty()) {
            blocking.add(new GateFinding(gate, gate + ":failed", null, null, verdict.get().summary()));
            return;
        }
        for (VerdictParser.Finding f : verdict.get().findings()) {
            blocking.add(new GateFinding(gate, gate + ":" + f.id(), f.file(), f.line(), f.message()));
        }
    }

    private boolean isRequiredCi(String name, RepoConfig config) {
        if (name.startsWith(AGENTIC_PREFIX)) {
            return false;
        }
        return config.requiredChecks().isEmpty() || config.requiredChecks().contains(name);
    }

    private List<GateFinding> ciFindings(String repo, CheckRun run) {
        List<Annotation> annotations = github.listCheckRunAnnotations(repo, run.id());
        if (annotations.isEmpty()) {
            String message = run.outputSummary() == null || run.outputSummary().isBlank() ? "Check " + run.conclusion() : run.outputSummary();
            return List.of(new GateFinding("ci", "ci:" + run.name(), null, null, message));
        }
        List<GateFinding> findings = new ArrayList<>();
        for (Annotation a : annotations) {
            String message = a.message().isBlank() ? a.title() : a.message();
            findings.add(new GateFinding("ci", "ci:" + run.name() + ":" + a.path() + ":" + a.title(), a.path(), a.startLine(), message));
        }
        return findings;
    }
}
