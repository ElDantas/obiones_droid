package io.agentic.functions.tasks;

import io.agentic.core.run.Actor;
import io.agentic.core.run.RunState;
import io.agentic.functions.notify.RunTransitions;
import io.agentic.functions.notify.SlackNotifier;
import io.agentic.functions.store.RunStore;
import io.agentic.functions.usage.UsageMeter;
import io.agentic.integrations.github.CopilotClient;
import io.agentic.integrations.github.CopilotUnavailableException;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static io.agentic.functions.support.Fixtures.run;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssignCopilotTaskTest {
    private final RunStore store = mock(RunStore.class);
    private final RunTransitions transitions = mock(RunTransitions.class);
    private final CopilotClient copilot = mock(CopilotClient.class);
    private final UsageMeter usage = mock(UsageMeter.class);
    private final SlackNotifier slack = mock(SlackNotifier.class);
    private final AssignCopilotTask task = new AssignCopilotTask(store, transitions, copilot, usage, slack);

    @Test
    void assignsAndMovesToCoding() {
        when(store.get("ABC-1")).thenReturn(Optional.of(run("ABC-1", RunState.CONTEXT, 101, null)));
        task.handleRequest(Map.of("ticketKey", "ABC-1"), null);
        verify(copilot).assignCopilot("acme/payments", 101);
        verify(transitions).moveTo("ABC-1", RunState.CODING, Actor.BOT, "Copilot assigned");
        verify(usage).recordCopilotSession("ABC-1");
    }

    @Test
    void unavailablePostsToSlackAndRethrows() {
        when(store.get("ABC-1")).thenReturn(Optional.of(run("ABC-1", RunState.CONTEXT, 101, null)));
        doThrow(new CopilotUnavailableException("acme/payments")).when(copilot).assignCopilot("acme/payments", 101);
        assertThatThrownBy(() -> task.handleRequest(Map.of("ticketKey", "ABC-1"), null)).isInstanceOf(CopilotUnavailableException.class);
        verify(slack).post(any(), argThat(t -> t.contains("not assignable")));
        verify(transitions, never()).moveTo(anyString(), any(), any(), anyString());
    }
}
