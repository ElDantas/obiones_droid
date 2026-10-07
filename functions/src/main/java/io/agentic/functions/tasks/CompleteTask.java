package io.agentic.functions.tasks;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import io.agentic.core.run.Actor;
import io.agentic.core.run.RunState;
import io.agentic.functions.config.Wiring;
import io.agentic.functions.notify.RunTransitions;

import java.util.Map;
import java.util.function.Supplier;

public class CompleteTask implements RequestHandler<Map<String, Object>, Map<String, Object>> {
    private final Supplier<RunTransitions> transitions;

    public CompleteTask() {
        this(Wiring::transitions);
    }

    CompleteTask(Supplier<RunTransitions> transitions) {
        this.transitions = transitions;
    }

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        String key = TaskSupport.ticketKey(input);
        transitions.get().moveTo(key, RunState.DONE, Actor.HUMAN, "PR merged");
        return Map.of("state", RunState.DONE.name());
    }
}
