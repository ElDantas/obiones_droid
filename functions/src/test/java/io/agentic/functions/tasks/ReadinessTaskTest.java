package io.agentic.functions.tasks;

import io.agentic.core.budget.Budgets;
import io.agentic.core.run.RunState;
import io.agentic.functions.config.Params;
import io.agentic.functions.readiness.ClarityScorer;
import io.agentic.functions.readiness.RepoConfig;
import io.agentic.functions.readiness.RepoConfigLoader;
import io.agentic.functions.store.RunStore;
import io.agentic.functions.support.Fixtures;
import io.agentic.functions.support.Tickets;
import io.agentic.integrations.jira.JiraClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReadinessTaskTest {
    private JiraClient jira;
    private Params params;
    private RunStore store;
    private ClarityScorer clarity;
    private RepoConfigLoader loader;
    private ReadinessTask task;

    @BeforeEach
    void setUp() {
        jira = mock(JiraClient.class);
        params = mock(Params.class);
        store = mock(RunStore.class);
        clarity = mock(ClarityScorer.class);
        loader = mock(RepoConfigLoader.class);
        when(jira.getTicket("ABC-1")).thenReturn(Tickets.ready());
        when(params.isEnabled("acme/payments")).thenReturn(true);
        when(params.find("/agentic/repos/allowlist")).thenReturn(Optional.of("[\"acme/payments\"]"));
        when(params.getInt("/agentic/readiness/maxStoryPoints", 5)).thenReturn(5);
        when(params.getInt("/agentic/readiness/clarityThreshold", 70)).thenReturn(70);
        when(store.get("ABC-1")).thenReturn(Optional.of(Fixtures.run("ABC-1", RunState.READINESS, null, null)));
        when(loader.load(any(), any())).thenReturn(new RepoConfig(Budgets.defaults(), List.of(), List.of(), List.of()));
        task = new ReadinessTask(jira, params, store, clarity, loader);
    }

    @Test
    void killSwitchOffForRepoIsPaused() {
        when(params.isEnabled("acme/payments")).thenReturn(false);
        assertThat(task.handleRequest(Map.of("ticketKey", "ABC-1"), null)).containsEntry("decision", "PAUSED");
    }

    @Test
    void lowClarityIsNotReadyWithClarifyReasons() {
        when(clarity.score(any())).thenReturn(new ClarityScorer.Clarity(50, List.of("What is the expected rounding?")));
        Map<String, Object> out = task.handleRequest(Map.of("ticketKey", "ABC-1"), null);
        assertThat(out).containsEntry("decision", "NOT_READY");
        assertThat((List<String>) out.get("reasons")).containsExactly("Clarify: What is the expected rounding?");
        verify(store, never()).setRepo(any(), any());
    }

    @Test
    void happyPathIsReadyAndStoresRepoAndBudgets() {
        when(clarity.score(any())).thenReturn(new ClarityScorer.Clarity(90, List.of()));
        Map<String, Object> out = task.handleRequest(Map.of("ticketKey", "ABC-1"), null);
        assertThat(out).containsEntry("decision", "READY").containsEntry("codingTimeoutSeconds", 3600L);
        verify(store).setRepo("ABC-1", "acme/payments");
        verify(store).saveBudgets("ABC-1", Budgets.defaults());
    }

    @Test
    void malformedRepoIsNotReady() {
        when(jira.getTicket("ABC-1")).thenReturn(Tickets.with("payments", "- a", 3.0, List.of()));
        assertThat(task.handleRequest(Map.of("ticketKey", "ABC-1"), null)).containsEntry("decision", "NOT_READY");
    }
}
