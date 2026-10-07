package io.agentic.functions.tasks;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import io.agentic.core.budget.BudgetEvaluator;
import io.agentic.core.budget.BudgetVerdict;
import io.agentic.core.loop.IterationSnapshot;
import io.agentic.core.run.Actor;
import io.agentic.core.run.RunState;
import io.agentic.core.scope.ScopeGuard;
import io.agentic.functions.config.Services;
import io.agentic.functions.config.Wiring;
import io.agentic.functions.gates.GateCollector;
import io.agentic.functions.gates.GateEvaluator;
import io.agentic.functions.gates.GateFindings;
import io.agentic.functions.loop.SnapshotBuilder;
import io.agentic.functions.usage.UsageMeter;
import io.agentic.functions.notify.RunTransitions;
import io.agentic.functions.readiness.RepoConfig;
import io.agentic.functions.readiness.RepoConfigLoader;
import io.agentic.functions.store.Run;
import io.agentic.functions.store.RunStore;
import io.agentic.integrations.github.GitHubClient;
import io.agentic.integrations.github.model.PullRequest;

import java.time.Clock;
import java.util.List;
import java.util.Map;

public class EvaluateGatesTask implements RequestHandler<Map<String, Object>, Map<String, Object>> {
    private final RunStore store;
    private final RunTransitions transitions;
    private final GitHubClient github;
    private final GateCollector collector;
    private final RepoConfigLoader configLoader;
    private final UsageMeter usage;
    private final Clock clock;
    private final GateEvaluator evaluator = new GateEvaluator();
    private final ScopeGuard scopeGuard = new ScopeGuard();
    private final SnapshotBuilder snapshots = new SnapshotBuilder();
    private final BudgetEvaluator budgets = new BudgetEvaluator();

    public EvaluateGatesTask() {
        this(Services.instance().runStore(), Wiring.transitions(), Services.instance().github(),
                Wiring.gateCollector(), new RepoConfigLoader(Services.instance().github()), Wiring.usageMeter(), Services.instance().clock());
    }

    EvaluateGatesTask(RunStore store, RunTransitions transitions, GitHubClient github, GateCollector collector, RepoConfigLoader configLoader,
                      UsageMeter usage, Clock clock) {
        this.store = store;
        this.transitions = transitions;
        this.github = github;
        this.collector = collector;
        this.configLoader = configLoader;
        this.usage = usage;
        this.clock = clock;
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
        BudgetVerdict verdict = budgets.evaluate(run.budgets(), run.usage(), clock.instant(), BudgetEvaluator.IterationKind.NONE);
        if (verdict.isBreach()) {
            return TaskSupport.decision("ESCALATE", String.join("; ", verdict.reasons()));
        }
        RepoConfig config = configLoader.load(run.repo(), run.budgets());
        PullRequest pr = github.getPullRequest(run.repo(), prNumber);
        GateFindings findings = collector.collect(run.repo(), prNumber, pr.headSha(), config, run.usage().gateIterations())
                .withScopeViolations(scopeGuard.check(config.budgets(), github.listFiles(run.repo(), prNumber)));
        store.setLastFindings(key, findings);
        findings.agentPremiumRequests().forEach((checkRunId, n) -> usage.recordGateAgentRequests(key, checkRunId, n));
        recordSnapshot(run, prNumber, pr.headSha(), findings);
        return evaluator.decide(findings, run.humanOverride(), true);
    }

    private void recordSnapshot(Run run, int prNumber, String headSha, GateFindings findings) {
        String previous = store.string(run.ticketKey(), "lastEvaluatedSha").orElse(null);
        var commits = SnapshotBuilder.commitsAfter(github.listCommits(run.repo(), prNumber), previous);
        IterationSnapshot snapshot = snapshots.build(run.snapshots().size() + 1, findings, commits,
                path -> github.readFile(run.repo(), path, headSha).orElse(null));
        store.appendSnapshot(run.ticketKey(), snapshot);
        store.setString(run.ticketKey(), "lastEvaluatedSha", headSha);
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
