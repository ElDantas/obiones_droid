package io.agentic.functions.tasks;

import io.agentic.core.budget.RunUsage;
import io.agentic.core.run.RunState;
import io.agentic.functions.notify.RunTransitions;
import io.agentic.functions.store.RunStore;
import io.agentic.functions.usage.UsageMeter;
import io.agentic.integrations.github.CopilotClient;
import io.agentic.integrations.github.GitHubClient;
import io.agentic.integrations.github.model.Review;
import io.agentic.integrations.github.model.ReviewComment;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.agentic.functions.support.Fixtures.run;
import static io.agentic.functions.support.Fixtures.withUsage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HumanFixTaskTest {
    private static final Instant NOW = Instant.parse("2026-10-07T13:00:00Z");
    private final RunStore store = mock(RunStore.class);
    private final GitHubClient github = mock(GitHubClient.class);
    private final CopilotClient copilot = mock(CopilotClient.class);
    private final UsageMeter usage = mock(UsageMeter.class);
    private final HumanFixTask task = new HumanFixTask(store, mock(RunTransitions.class), github, copilot, usage, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void limitReachedEscalatesAsUnderspecified() {
        when(store.get("ABC-1")).thenReturn(Optional.of(withUsage(run("ABC-1", RunState.HUMAN_FIX, 101, 418), new RunUsage(0, 3, 5, 10, NOW), false)));
        Map<String, Object> out = task.handleRequest(Map.of("ticketKey", "ABC-1", "signal", Map.of("reviewId", 9001)), null);
        assertThat(out).containsEntry("decision", "ESCALATE");
        assertThat((String) out.get("reason")).contains("may be underspecified");
        verify(copilot, never()).instruct(anyString(), anyInt(), anyString());
    }

    @Test
    void forwardsReviewToCopilot() {
        when(store.get("ABC-1")).thenReturn(Optional.of(withUsage(run("ABC-1", RunState.HUMAN_FIX, 101, 418), new RunUsage(0, 0, 5, 10, NOW), false)));
        when(github.listReviewCommentsForReview("acme/payments", 418, 9001)).thenReturn(List.of(new ReviewComment(1, "alice", "src/A.java", 3, "Use repo", "s", 9001L)));
        when(github.listReviews("acme/payments", 418)).thenReturn(List.of(new Review(9001, "alice", "CHANGES_REQUESTED", "Please refactor", "s")));
        assertThat(task.handleRequest(Map.of("ticketKey", "ABC-1", "signal", Map.of("reviewId", 9001)), null)).containsEntry("decision", "CONTINUE");
        verify(copilot).instruct(eq("acme/payments"), eq(418), argThat(t -> t.contains("Please refactor") && t.contains("Use repo")));
        verify(usage).recordHumanIteration("ABC-1");
    }
}
