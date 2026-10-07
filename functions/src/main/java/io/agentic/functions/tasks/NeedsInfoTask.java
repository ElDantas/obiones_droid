package io.agentic.functions.tasks;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import io.agentic.core.run.Actor;
import io.agentic.core.run.RunState;
import io.agentic.functions.config.Wiring;
import io.agentic.functions.notify.RunTransitions;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class NeedsInfoTask implements RequestHandler<Map<String, Object>, Map<String, Object>> {
    private final Supplier<RunTransitions> transitions;

    public NeedsInfoTask() {
        this(Wiring::transitions);
    }

    NeedsInfoTask(Supplier<RunTransitions> transitions) {
        this.transitions = transitions;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        String key = TaskSupport.ticketKey(input);
        Map<String, Object> readiness = input.get("readiness") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        List<String> reasons = readiness.get("reasons") instanceof List<?> l ? (List<String>) l : List.of("Ticket is not agent-ready");
        transitions.get().moveTo(key, RunState.NEEDS_INFO, Actor.BOT, String.join("; ", reasons));
        return Map.of("state", RunState.NEEDS_INFO.name());
    }
}
