package io.agentic.functions.tasks;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import io.agentic.core.budget.BudgetEvaluator;
import io.agentic.core.budget.BudgetVerdict;
import io.agentic.core.run.Actor;
import io.agentic.core.run.RunState;
import io.agentic.functions.config.Services;
import io.agentic.functions.config.Wiring;
import io.agentic.functions.gates.FeedbackComposer;
import io.agentic.functions.gates.GateFindings;
import io.agentic.functions.notify.RunTransitions;
import io.agentic.functions.readiness.RepoConfigLoader;
import io.agentic.functions.store.Run;
import io.agentic.functions.store.RunStore;
import io.agentic.functions.usage.UsageMeter;
import io.agentic.integrations.github.CopilotClient;

import java.time.Clock;
import java.util.Map;

public class FixingTask implements RequestHandler<Map<String, Object>, Map<String, Object>> {
    private final RunStore store;
    private final RunTransitions transitions;
    private final CopilotClient copilot;
    private final UsageMeter usage;
    private final RepoConfigLoader configLoader;
    private final Clock clock;
    private final BudgetEvaluator budgets = new BudgetEvaluator();
    private final FeedbackComposer feedback = new FeedbackComposer();

    public FixingTask() {
        this(Services.instance().runStore(), Wiring.transitions(), Services.instance().copilot(),
                new UsageMeter(Services.instance().runStore()), new RepoConfigLoader(Services.instance().github()), Services.instance().clock());
    }

    FixingTask(RunStore store, RunTransitions transitions, CopilotClient copilot, UsageMeter usage, RepoConfigLoader configLoader, Clock clock) {
        this.store = store;
        this.transitions = transitions;
        this.copilot = copilot;
        this.usage = usage;
        this.configLoader = configLoader;
        this.clock = clock;
    }

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        String key = TaskSupport.ticketKey(input);
        transitions.moveTo(key, RunState.FIXING, Actor.BOT, "Gates failed");
        Run run = store.get(key).orElseThrow();
        if (run.humanOverride()) {
            return TaskSupport.decision("HUMAN_OVERRIDE", "A human pushed to the PR branch");
        }
        BudgetVerdict verdict = budgets.evaluate(run.budgets(), run.usage(), clock.instant(), BudgetEvaluator.IterationKind.GATE);
        if (verdict.isBreach()) {
            return TaskSupport.decision("ESCALATE", String.join("; ", verdict.reasons()));
        }
        GateFindings findings = store.lastFindings(key, GateFindings.class).orElse(GateFindings.empty(null));
        copilot.instruct(run.repo(), run.prNumber(), feedback.forGates(findings, configLoader.load(run.repo(), run.budgets())));
        usage.recordGateIteration(key);
        usage.recordCopilotSession(key);
        return TaskSupport.decision("CONTINUE", "Feedback sent to Copilot");
    }
}
