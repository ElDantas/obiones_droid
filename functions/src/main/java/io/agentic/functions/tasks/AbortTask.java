package io.agentic.functions.tasks;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import io.agentic.core.run.Actor;
import io.agentic.functions.config.Wiring;
import io.agentic.functions.run.AbortService;

import java.util.Map;
import java.util.function.Supplier;

public class AbortTask implements RequestHandler<Map<String, Object>, Map<String, Object>> {
    private final Supplier<AbortService> abort;

    public AbortTask() {
        this(Wiring::abortService);
    }

    AbortTask(Supplier<AbortService> abort) {
        this.abort = abort;
    }

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        String key = TaskSupport.ticketKey(input);
        String reason = TaskSupport.string(input, "abortReason", "Aborted");
        abort.get().abort(key, Actor.BOT, reason, false);
        return Map.of("aborted", true);
    }
}
