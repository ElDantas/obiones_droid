package io.agentic.functions.tasks;

import io.agentic.functions.store.RunStore;
import io.agentic.functions.store.WaitKind;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sfn.SfnClient;
import software.amazon.awssdk.services.sfn.model.SendTaskSuccessRequest;

import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegisterWaitTaskTest {
    private final RunStore store = mock(RunStore.class);
    private final SfnClient sfn = mock(SfnClient.class);
    private final RegisterWaitTask task = new RegisterWaitTask(() -> store, () -> sfn);
    private final Map<String, Object> input = Map.of("ticketKey", "ABC-1", "kind", "CHECKS_COMPLETE", "taskToken", "tok");

    @Test
    void noPendingSignalJustRegisters() {
        when(store.registerWait("ABC-1", WaitKind.CHECKS_COMPLETE, "tok")).thenReturn(Optional.empty());
        task.handleRequest(input, null);
        verify(sfn, never()).sendTaskSuccess(any(SendTaskSuccessRequest.class));
    }

    @Test
    void pendingSignalCompletesImmediately() {
        when(store.registerWait("ABC-1", WaitKind.CHECKS_COMPLETE, "tok")).thenReturn(Optional.of("{\"headSha\":\"a\"}"));
        task.handleRequest(input, null);
        verify(sfn, times(1)).sendTaskSuccess(SendTaskSuccessRequest.builder().taskToken("tok").output("{\"headSha\":\"a\"}").build());
    }
}
