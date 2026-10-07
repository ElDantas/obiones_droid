package io.agentic.functions.run;

import io.agentic.functions.ingress.RoutedSignal;
import io.agentic.functions.store.RunStore;
import io.agentic.functions.store.WaitKind;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sfn.SfnClient;
import software.amazon.awssdk.services.sfn.model.SendTaskSuccessRequest;
import software.amazon.awssdk.services.sfn.model.TaskTimedOutException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SignalDispatcherTest {
    private final RunStore store = mock(RunStore.class);
    private final SfnClient sfn = mock(SfnClient.class);
    private final SignalDispatcher dispatcher = new SignalDispatcher(store, sfn);
    private final RoutedSignal signal = new RoutedSignal("ABC-1", WaitKind.CHECKS_COMPLETE, "{\"headSha\":\"a\"}");

    @Test
    void earlySignalIsStoredAndNotSent() {
        when(store.deliverSignal("ABC-1", WaitKind.CHECKS_COMPLETE, "{\"headSha\":\"a\"}")).thenReturn(Optional.empty());
        assertThat(dispatcher.dispatch(signal)).isFalse();
        verify(sfn, never()).sendTaskSuccess(any(SendTaskSuccessRequest.class));
    }

    @Test
    void tokenPresentSendsSuccess() {
        when(store.deliverSignal("ABC-1", WaitKind.CHECKS_COMPLETE, "{\"headSha\":\"a\"}")).thenReturn(Optional.of("tok"));
        assertThat(dispatcher.dispatch(signal)).isTrue();
        verify(sfn).sendTaskSuccess(SendTaskSuccessRequest.builder().taskToken("tok").output("{\"headSha\":\"a\"}").build());
    }

    @Test
    void timedOutTokenIsSwallowed() {
        when(store.deliverSignal("ABC-1", WaitKind.CHECKS_COMPLETE, "{\"headSha\":\"a\"}")).thenReturn(Optional.of("tok"));
        when(sfn.sendTaskSuccess(any(SendTaskSuccessRequest.class))).thenThrow(TaskTimedOutException.builder().message("late").build());
        assertThat(dispatcher.dispatch(signal)).isFalse();
    }
}
