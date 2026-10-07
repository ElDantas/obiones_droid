package io.agentic.functions.tasks;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import io.agentic.functions.config.Services;
import io.agentic.functions.store.RunStore;
import io.agentic.functions.store.WaitKind;
import software.amazon.awssdk.services.sfn.SfnClient;
import software.amazon.awssdk.services.sfn.model.SendTaskSuccessRequest;

import java.util.Map;
import java.util.function.Supplier;

public class RegisterWaitTask implements RequestHandler<Map<String, Object>, Map<String, Object>> {
    private final Supplier<RunStore> runStore;
    private final Supplier<SfnClient> sfn;

    public RegisterWaitTask() {
        this(() -> Services.instance().runStore(), () -> Services.instance().sfn());
    }

    RegisterWaitTask(Supplier<RunStore> runStore, Supplier<SfnClient> sfn) {
        this.runStore = runStore;
        this.sfn = sfn;
    }

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        String ticketKey = TaskSupport.ticketKey(input);
        WaitKind kind = WaitKind.valueOf((String) input.get("kind"));
        String token = (String) input.get("taskToken");
        runStore.get().registerWait(ticketKey, kind, token).ifPresent(payload ->
                sfn.get().sendTaskSuccess(SendTaskSuccessRequest.builder().taskToken(token).output(payload).build()));
        return Map.of("registered", kind.name());
    }
}
