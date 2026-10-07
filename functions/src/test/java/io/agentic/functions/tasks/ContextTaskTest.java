package io.agentic.functions.tasks;

import io.agentic.core.budget.Budgets;
import io.agentic.core.run.Actor;
import io.agentic.core.run.RunState;
import io.agentic.functions.context.Lesson;
import io.agentic.functions.notify.RunTransitions;
import io.agentic.functions.readiness.RepoConfig;
import io.agentic.functions.readiness.RepoConfigLoader;
import io.agentic.functions.store.RunStore;
import io.agentic.functions.support.Fixtures;
import io.agentic.functions.support.Tickets;
import io.agentic.integrations.github.GitHubClient;
import io.agentic.integrations.jira.JiraClient;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContextTaskTest {
    private final RunStore store = mock(RunStore.class);
    private final RunTransitions transitions = mock(RunTransitions.class);
    private final JiraClient jira = mock(JiraClient.class);
    private final GitHubClient github = mock(GitHubClient.class);
    private final RepoConfigLoader loader = mock(RepoConfigLoader.class);

    @Test
    void createsIssueAndRecordsLessons() {
        when(store.get("ABC-1")).thenReturn(Optional.of(Fixtures.run("ABC-1", RunState.CONTEXT, null, null)));
        when(jira.getTicket("ABC-1")).thenReturn(Tickets.ready());
        when(jira.browseUrl("ABC-1")).thenReturn("https://jira/browse/ABC-1");
        when(loader.load(any(), any())).thenReturn(new RepoConfig(Budgets.defaults(), List.of(), List.of(), List.of()));
        when(github.createIssue(eq("acme/payments"), eq("[ABC-1] Cap discounts"), anyString())).thenReturn(101);
        ContextTask task = new ContextTask(store, transitions, jira, github, loader,
                (t, r) -> List.of(new Lesson("l1", "t", "l", List.of())));

        task.handleRequest(Map.of("ticketKey", "ABC-1"), null);

        verify(transitions).moveTo(eq("ABC-1"), eq(RunState.CONTEXT), eq(Actor.BOT), anyString());
        verify(store).setIssue("ABC-1", 101);
        verify(store).setInjectedLessons("ABC-1", List.of("l1"));
    }

    @Test
    void retryDoesNotCreateASecondIssue() {
        when(store.get("ABC-1")).thenReturn(Optional.of(Fixtures.run("ABC-1", RunState.CONTEXT, 101, null)));
        new ContextTask(store, transitions, jira, github, loader, (t, r) -> List.of()).handleRequest(Map.of("ticketKey", "ABC-1"), null);
        verify(github, never()).createIssue(anyString(), anyString(), anyString());
    }
}
