package io.agentic.functions.tasks;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import io.agentic.core.run.Actor;
import io.agentic.core.run.RunState;
import io.agentic.functions.config.Services;
import io.agentic.functions.config.Wiring;
import io.agentic.functions.notify.RunTransitions;
import io.agentic.functions.store.Run;
import io.agentic.functions.store.RunStore;

import java.util.Map;

public class ApplyEscalationDecisionTask implements RequestHandler<Map<String, Object>, Map<String, Object>> {
    private final RunStore store;
    private final RunTransitions transitions;
    private final java.util.function.Supplier<io.agentic.functions.learning.LessonRecorder> recorder;

    public ApplyEscalationDecisionTask() {
        this(Services.instance().runStore(), Wiring.transitions(), Wiring::lessonRecorder);
    }

    ApplyEscalationDecisionTask(RunStore store, RunTransitions transitions, java.util.function.Supplier<io.agentic.functions.learning.LessonRecorder> recorder) {
        this.store = store;
        this.transitions = transitions;
        this.recorder = recorder;
    }

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        String key = TaskSupport.ticketKey(input);
        String decision = String.valueOf(TaskSupport.signal(input).getOrDefault("decision", "ABORT"));
        Run run = store.get(key).orElseThrow();
        if ("RAISE_BUDGET".equals(decision)) {
            store.saveBudgets(key, run.budgets().raisedBy(1.5));
        }
        RunState from = run.escalatedFrom() == null ? RunState.FIXING : run.escalatedFrom();
        try {
            recorder.get().recordPostMortem(run.repo(), String.valueOf(run.escalation().getOrDefault("reason", "")),
                    String.valueOf(run.escalation().getOrDefault("diagnosis", "")), decision,
                    run.prNumber() == null ? "" : "https://github.com/" + run.repo() + "/pull/" + run.prNumber());
        } catch (RuntimeException e) {
            System.err.println("WARN post-mortem lesson failed for " + key + ": " + e.getMessage());
        }
        boolean beforeCoding = from == RunState.READINESS || from == RunState.CONTEXT;
        String result = switch (decision) {
            case "ABORT" -> "ABORT";
            case "RESUME", "RAISE_BUDGET" -> beforeCoding ? "ABORT" : null;
            case "TAKE_OVER" -> "TAKE_OVER";
            default -> "ABORT";
        };
        if (result == null) {
            result = switch (from) {
                case CODING -> "RESUME_CODING";
                case GATES -> "RESUME_GATES";
                default -> "RESUME_FIXING";
            };
        }
        RunState target = switch (result) {
            case "RESUME_CODING" -> RunState.CODING;
            case "RESUME_GATES" -> RunState.GATES;
            case "RESUME_FIXING" -> RunState.FIXING;
            default -> null;
        };
        if ("RESUME".equals(decision) || "RAISE_BUDGET".equals(decision)) {
            store.setFlag(key, "loopOverride", true);
        }
        if (target != null) {
            transitions.moveTo(key, target, Actor.HUMAN, "Escalation resolved: " + decision);
        }
        String reason = beforeCoding && "ABORT".equals(result) && !"ABORT".equals(decision)
                ? "Cannot resume before coding started; comment /agent restart on the Jira ticket"
                : "Escalation resolved: " + decision;
        return TaskSupport.decision(result, reason);
    }
}
