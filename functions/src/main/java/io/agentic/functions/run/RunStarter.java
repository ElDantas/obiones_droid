package io.agentic.functions.run;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.agentic.core.budget.Budgets;
import io.agentic.functions.config.Params;
import io.agentic.functions.store.RunStore;
import io.agentic.integrations.http.Json;
import software.amazon.awssdk.services.sfn.SfnClient;
import software.amazon.awssdk.services.sfn.model.StartExecutionRequest;
import software.amazon.awssdk.services.sfn.model.StartExecutionResponse;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

public class RunStarter {
    public enum StartResult { STARTED, ALREADY_RUNNING, DISABLED }

    private final Params params;
    private final RunStore runStore;
    private final SfnClient sfn;
    private final Clock clock;
    private final Supplier<String> stateMachineArn;

    public RunStarter(Params params, RunStore runStore, SfnClient sfn, Clock clock, Supplier<String> stateMachineArn) {
        this.params = params;
        this.runStore = runStore;
        this.sfn = sfn;
        this.clock = clock;
        this.stateMachineArn = stateMachineArn;
    }

    public StartResult start(String ticketKey) {
        if (!params.isGloballyEnabled()) {
            return StartResult.DISABLED;
        }
        String runId = UUID.randomUUID().toString();
        Budgets defaults = params.find("/agentic/budgets/defaults").isPresent()
                ? params.getJson("/agentic/budgets/defaults", Budgets.class)
                : Budgets.defaults();
        if (!runStore.tryStart(ticketKey, runId, "", defaults, clock.instant())) {
            return StartResult.ALREADY_RUNNING;
        }
        StartExecutionResponse res = sfn.startExecution(StartExecutionRequest.builder()
                .stateMachineArn(stateMachineArn.get())
                .name(ticketKey + "-" + clock.instant().getEpochSecond())
                .input(input(ticketKey, runId))
                .build());
        runStore.setExecutionArn(ticketKey, res.executionArn());
        return StartResult.STARTED;
    }

    private static String input(String ticketKey, String runId) {
        try {
            return Json.MAPPER.writeValueAsString(Map.of("ticketKey", ticketKey, "runId", runId));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
