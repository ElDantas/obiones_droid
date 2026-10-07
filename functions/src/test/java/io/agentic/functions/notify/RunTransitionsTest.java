package io.agentic.functions.notify;

import io.agentic.core.run.Actor;
import io.agentic.core.run.RunState;
import io.agentic.functions.store.RunStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static io.agentic.functions.support.Fixtures.run;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RunTransitionsTest {
    private final RunStore store = mock(RunStore.class);
    private final Notifier first = mock(Notifier.class);
    private final Notifier second = mock(Notifier.class);
    private final RunTransitions transitions = new RunTransitions(store, List.of(first, second));

    @Test
    void sameStateDoesNotNotify() {
        when(store.get("ABC-1")).thenReturn(Optional.of(run("ABC-1", RunState.GATES, 1, 2)));
        transitions.moveTo("ABC-1", RunState.GATES, Actor.BOT, "again");
        verify(first, never()).onTransition(any(), any(), any(), anyString());
    }

    @Test
    void failingNotifierDoesNotStopOthersOrThrow() {
        when(store.get("ABC-1")).thenReturn(Optional.of(run("ABC-1", RunState.CODING, 1, 2)), Optional.of(run("ABC-1", RunState.GATES, 1, 2)));
        doThrow(new RuntimeException("slack down")).when(first).onTransition(any(), any(), any(), anyString());
        transitions.moveTo("ABC-1", RunState.GATES, Actor.BOT, "go");
        verify(store).moveTo("ABC-1", RunState.GATES, Actor.BOT, "go");
        verify(second).onTransition(any(), any(), any(), anyString());
    }
}
