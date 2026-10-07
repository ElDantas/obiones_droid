package io.agentic.functions.run;

import io.agentic.core.run.Actor;
import io.agentic.core.run.RunState;
import io.agentic.functions.ingress.Identities;
import io.agentic.functions.store.RunStore;
import io.agentic.integrations.github.GitHubClient;
import io.agentic.integrations.github.model.PullRequest;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sfn.SfnClient;
import software.amazon.awssdk.services.sfn.model.StopExecutionRequest;

import java.util.Optional;

import static io.agentic.functions.support.Fixtures.run;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AbortServiceTest {
    private final RunStore store = mock(RunStore.class);
    private final SfnClient sfn = mock(SfnClient.class);
    private final GitHubClient github = mock(GitHubClient.class);
    private final AbortService.Mover mover = mock(AbortService.Mover.class);
    private final AbortService service = new AbortService(store, sfn, github, new Identities("agentic-svc", "agentic-bot[bot]"), mover);

    @Test
    void abortsStopsAndClosesCopilotPr() {
        when(store.get("ABC-1")).thenReturn(Optional.of(run("ABC-1", RunState.FIXING, 101, 418)));
        when(github.getPullRequest("acme/payments", 418)).thenReturn(new PullRequest(418, "open", true, false, "a", "b", "Copilot", "u", 0, 0, 0, "n"));
        assertThat(service.abort("ABC-1", Actor.HUMAN, "Label removed")).isTrue();
        verify(sfn).stopExecution(any(StopExecutionRequest.class));
        verify(github).closePullRequest("acme/payments", 418);
        verify(mover).moveTo("ABC-1", RunState.ABORTED, Actor.HUMAN, "Label removed");
    }

    @Test
    void terminalRunIsNoop() {
        when(store.get("ABC-2")).thenReturn(Optional.of(run("ABC-2", RunState.DONE, 101, 418)));
        assertThat(service.abort("ABC-2", Actor.HUMAN, "x")).isFalse();
        verify(mover, never()).moveTo(anyString(), any(), any(), anyString());
    }

    @Test
    void withoutStopDoesNotStopExecution() {
        when(store.get("ABC-3")).thenReturn(Optional.of(run("ABC-3", RunState.HUMAN_REVIEW, 101, null)));
        service.abort("ABC-3", Actor.BOT, "PR closed", false);
        verify(sfn, never()).stopExecution(any(StopExecutionRequest.class));
    }
}
