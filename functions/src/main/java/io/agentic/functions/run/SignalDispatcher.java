package io.agentic.functions.run;

import io.agentic.functions.ingress.RoutedSignal;
import io.agentic.functions.store.RunStore;
import software.amazon.awssdk.services.sfn.SfnClient;
import software.amazon.awssdk.services.sfn.model.InvalidTokenException;
import software.amazon.awssdk.services.sfn.model.SendTaskSuccessRequest;
import software.amazon.awssdk.services.sfn.model.TaskDoesNotExistException;
import software.amazon.awssdk.services.sfn.model.TaskTimedOutException;

import java.util.Optional;

public class SignalDispatcher {
    private final RunStore runStore;
    private final SfnClient sfn;

    public SignalDispatcher(RunStore runStore, SfnClient sfn) {
        this.runStore = runStore;
        this.sfn = sfn;
    }

    public boolean dispatch(RoutedSignal signal) {
        Optional<String> token = runStore.deliverSignal(signal.ticketKey(), signal.kind(), signal.payloadJson());
        if (token.isEmpty()) {
            return false;
        }
        try {
            sfn.sendTaskSuccess(SendTaskSuccessRequest.builder().taskToken(token.get()).output(signal.payloadJson()).build());
            return true;
        } catch (TaskTimedOutException | InvalidTokenException | TaskDoesNotExistException e) {
            System.err.println("Stale task token for " + signal.ticketKey() + " " + signal.kind() + ": " + e.getClass().getSimpleName());
            return false;
        }
    }
}
