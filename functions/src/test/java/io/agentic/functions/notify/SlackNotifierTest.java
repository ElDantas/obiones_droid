package io.agentic.functions.notify;

import io.agentic.core.budget.Budgets;
import io.agentic.core.budget.RunUsage;
import io.agentic.core.run.RunState;
import io.agentic.functions.store.Run;
import io.agentic.functions.store.RunStore;
import io.agentic.integrations.jira.JiraClient;
import io.agentic.integrations.jira.JiraTicket;
import io.agentic.integrations.slack.SlackClient;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static io.agentic.functions.support.Fixtures.run;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SlackNotifierTest {
    private final SlackClient slack = mock(SlackClient.class);
    private final JiraClient jira = mock(JiraClient.class);
    private final RunStore store = mock(RunStore.class);
    private final Links links = new Links("https://github.com", "https://corp.atlassian.net");
    private final SlackNotifier notifier = new SlackNotifier(slack, jira, store, () -> "#agentic-dev", links);

    private static Run withThread(String ts) {
        Run r = run("ABC-1", RunState.FIXING, 101, 418);
        return new Run(r.ticketKey(), r.runId(), r.repo(), r.state(), r.issueNumber(), r.prNumber(), r.executionArn(), ts,
                Budgets.defaults(), RunUsage.start(Instant.now()), List.of(), false, List.of(), Map.of(), null);
    }

    @Test
    void firstTransitionCreatesRootAndStoresThread() {
        when(jira.getTicket("ABC-1")).thenReturn(new JiraTicket("ABC-1", "Cap discounts", "", "", "acme/payments", 3.0, List.of(), List.of(), null, null, List.of()));
        when(slack.post(eq("#agentic-dev"), isNull(), isNull(), anyString())).thenReturn("100.1");
        notifier.onTransition(run("ABC-1", RunState.CONTEXT, null, null), RunState.READINESS, RunState.CONTEXT, "ready");
        verify(store).setSlackThread("ABC-1", "100.1");
        verify(slack).post(eq("#agentic-dev"), eq("100.1"), isNull(), anyString());
    }

    @Test
    void laterTransitionsReplyInThread() {
        notifier.onTransition(withThread("100.1"), RunState.GATES, RunState.FIXING, "1 blocking finding(s)");
        verify(slack, never()).post(eq("#agentic-dev"), isNull(), isNull(), anyString());
        verify(slack).post(eq("#agentic-dev"), eq("100.1"), isNull(), argThat(t -> t.startsWith("🔧 Fixing")));
    }

    @Test
    void reasonIsScrubbed() {
        notifier.onTransition(withThread("100.1"), RunState.GATES, RunState.FIXING, "token ghp_abcdefghijklmnopqrstuvwxyz0123456789 leaked");
        verify(slack).post(eq("#agentic-dev"), eq("100.1"), isNull(),
                argThat(t -> t.contains("[REDACTED_GITHUB_TOKEN]") && !t.contains("ghp_abcdefghijklmnopqrstuvwxyz0123456789")));
    }
}
