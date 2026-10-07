package io.agentic.functions.tasks;

import io.agentic.core.budget.Budgets;
import io.agentic.core.run.RunState;
import io.agentic.functions.gates.GateCollector;
import io.agentic.functions.gates.GateFindings;
import io.agentic.functions.notify.RunTransitions;
import io.agentic.functions.readiness.RepoConfig;
import io.agentic.functions.readiness.RepoConfigLoader;
import io.agentic.functions.store.RunStore;
import io.agentic.integrations.github.GitHubClient;
import io.agentic.integrations.github.model.PullRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.agentic.functions.support.Fixtures.run;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EvaluateGatesTaskTest {
    private RunStore store;
    private GitHubClient github;
    private GateCollector collector;
    private RepoConfigLoader loader;
    private EvaluateGatesTask task;

    @BeforeEach
    void setUp() {
        store = mock(RunStore.class);
        github = mock(GitHubClient.class);
        collector = mock(GateCollector.class);
        loader = mock(RepoConfigLoader.class);
        when(loader.load(any(), any())).thenReturn(new RepoConfig(Budgets.defaults(), List.of(), List.of(), List.of()));
        task = new EvaluateGatesTask(store, mock(RunTransitions.class), github, collector, loader);
    }

    @Test
    void noPrLinkedEscalates() {
        when(store.get("ABC-1")).thenReturn(Optional.of(run("ABC-1", RunState.CODING, 101, null)));
        when(github.openPullRequestsForIssue("acme/payments", 101)).thenReturn(List.of());
        assertThat(task.handleRequest(Map.of("ticketKey", "ABC-1"), null))
                .containsEntry("decision", "ESCALATE").containsEntry("reason", "No PR linked to the issue");
    }

    @Test
    void severalOpenPrsEscalateAsAmbiguous() {
        when(store.get("ABC-1")).thenReturn(Optional.of(run("ABC-1", RunState.CODING, 101, null)));
        when(github.openPullRequestsForIssue("acme/payments", 101)).thenReturn(List.of(418, 419));
        assertThat(task.handleRequest(Map.of("ticketKey", "ABC-1"), null))
                .containsEntry("decision", "ESCALATE")
                .hasEntrySatisfying("reason", r -> assertThat((String) r).contains("more than one PR"));
    }

    @Test
    void singleDiscoveredPrIsLinkedAndEvaluated() {
        when(store.get("ABC-1")).thenReturn(Optional.of(run("ABC-1", RunState.CODING, 101, null)));
        when(github.openPullRequestsForIssue("acme/payments", 101)).thenReturn(List.of(418));
        when(github.getPullRequest("acme/payments", 418)).thenReturn(new PullRequest(418, "open", true, false, "sha", "b", "Copilot", "u", 1, 1, 1, "n"));
        when(collector.collect(eq("acme/payments"), eq(418), eq("sha"), any(), anyInt())).thenReturn(GateFindings.empty("sha"));
        when(github.listFiles("acme/payments", 418)).thenReturn(List.of());
        assertThat(task.handleRequest(Map.of("ticketKey", "ABC-1"), null)).containsEntry("decision", "PASS");
        verify(store).setPr("ABC-1", 418);
        verify(store).setLastFindings(eq("ABC-1"), any());
    }

    @Test
    void forbiddenPathEscalates() {
        when(store.get("ABC-1")).thenReturn(Optional.of(run("ABC-1", RunState.CODING, 101, 418)));
        when(github.getPullRequest("acme/payments", 418)).thenReturn(new PullRequest(418, "open", true, false, "sha", "b", "Copilot", "u", 1, 1, 1, "n"));
        when(collector.collect(anyString(), anyInt(), anyString(), any(), anyInt())).thenReturn(GateFindings.empty("sha"));
        when(github.listFiles("acme/payments", 418)).thenReturn(List.of(new io.agentic.core.scope.ScopeGuard.ChangedFile("infra/main.tf", 1, 0)));
        assertThat(task.handleRequest(Map.of("ticketKey", "ABC-1"), null)).containsEntry("decision", "ESCALATE");
    }
}
