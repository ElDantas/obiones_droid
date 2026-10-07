package io.agentic.functions.notify;

import io.agentic.core.run.RunState;
import io.agentic.integrations.jira.JiraClient;
import io.agentic.integrations.jira.JiraTransitionMissing;
import io.agentic.integrations.slack.SlackClient;
import org.junit.jupiter.api.Test;

import static io.agentic.functions.support.Fixtures.run;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class JiraNotifierTest {
    private final JiraClient jira = mock(JiraClient.class);
    private final SlackClient slack = mock(SlackClient.class);
    private final JiraNotifier notifier = new JiraNotifier(jira, slack, () -> "#ops", () -> "https://wiki/agent-ready",
            new Links("https://github.com", "https://corp.atlassian.net"));

    @Test
    void humanReviewTransitionsAndComments() {
        notifier.onTransition(run("ABC-1", RunState.HUMAN_REVIEW, 101, 418), RunState.GATES, RunState.HUMAN_REVIEW, "ok");
        verify(jira).transition("ABC-1", "In Review");
        verify(jira).comment(eq("ABC-1"), argThat(t -> t.equals("PR ready for review: https://github.com/acme/payments/pull/418")));
    }

    @Test
    void leavingEscalatedGoesBackToInProgress() {
        notifier.onTransition(run("ABC-1", RunState.GATES, 101, 418), RunState.ESCALATED, RunState.GATES, "resumed");
        verify(jira).transition("ABC-1", "In Progress");
    }

    @Test
    void codingToGatesTouchesNothing() {
        notifier.onTransition(run("ABC-1", RunState.GATES, 101, 418), RunState.CODING, RunState.GATES, "checks");
        verifyNoInteractions(jira);
    }

    @Test
    void needsInfoListsReasonsAndLinksDefinition() {
        notifier.onTransition(run("ABC-1", RunState.NEEDS_INFO, null, null), RunState.READINESS, RunState.NEEDS_INFO, "No acceptance criteria found; Story points are not set");
        verify(jira).transition("ABC-1", "Blocked (Agent)");
        verify(jira).comment(eq("ABC-1"), argThat(t -> t.contains("- No acceptance criteria found\n- Story points are not set") && t.contains("https://wiki/agent-ready")));
    }

    @Test
    void missingTransitionWarnsOpsOncePerTicket() {
        doThrow(new JiraTransitionMissing("ABC-1", "In Review")).when(jira).transition(anyString(), anyString());
        notifier.onTransition(run("ABC-1", RunState.HUMAN_REVIEW, 1, 2), RunState.GATES, RunState.HUMAN_REVIEW, "x");
        notifier.onTransition(run("ABC-1", RunState.HUMAN_REVIEW, 1, 2), RunState.GATES, RunState.HUMAN_REVIEW, "x");
        verify(slack, times(1)).post(eq("#ops"), isNull(), isNull(), anyString());
        verify(jira, times(2)).comment(eq("ABC-1"), anyString());
        verify(slack, never()).post(eq("#agentic-dev"), anyString(), isNull(), anyString());
    }
}
