package io.agentic.functions.tasks;

import io.agentic.core.budget.Budgets;
import io.agentic.core.budget.RunUsage;
import io.agentic.core.run.RunState;
import io.agentic.functions.gates.GateFindings;
import io.agentic.functions.notify.RunTransitions;
import io.agentic.functions.readiness.RepoConfig;
import io.agentic.functions.readiness.RepoConfigLoader;
import io.agentic.functions.store.RunStore;
import io.agentic.functions.usage.UsageMeter;
import io.agentic.integrations.github.CopilotClient;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FixingTaskTest {
    private static final Instant NOW = Instant.parse("2026-10-07T13:00:00Z");
    private RunStore store;
    private CopilotClient copilot;
    private UsageMeter usage;
    private FixingTask task;

    @BeforeEach
    void setUp() {
        store = mock(RunStore.class);
        copilot = mock(CopilotClient.class);
        usage = mock(UsageMeter.class);
        RepoConfigLoader loader = mock(RepoConfigLoader.class);
        when(loader.load(any(), any())).thenReturn(new RepoConfig(Budgets.defaults(), List.of(), List.of(), List.of()));
        when(store.lastFindings("ABC-1", GateFindings.class)).thenReturn(Optional.of(GateFindings.empty("abc")));
        task = new FixingTask(store, mock(RunTransitions.class), copilot, usage, loader, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void iterationLimitEscalatesWithoutComment() {
        when(store.get("ABC-1")).thenReturn(Optional.of(withUsage(run("ABC-1", RunState.FIXING, 101, 418), new RunUsage(3, 0, 5, 10, NOW), false)));
        assertThat(task.handleRequest(Map.of("ticketKey", "ABC-1"), null)).containsEntry("decision", "ESCALATE");
        verify(copilot, never()).instruct(anyString(), anyInt(), anyString());
    }

    @Test
    void underLimitInstructsCopilotAndCountsUsage() {
        when(store.get("ABC-1")).thenReturn(Optional.of(withUsage(run("ABC-1", RunState.FIXING, 101, 418), new RunUsage(1, 0, 5, 10, NOW), false)));
        assertThat(task.handleRequest(Map.of("ticketKey", "ABC-1"), null)).containsEntry("decision", "CONTINUE");
        verify(copilot).instruct(eq("acme/payments"), eq(418), any());
        verify(usage).recordGateIteration("ABC-1");
        verify(usage).recordCopilotSession("ABC-1");
    }

    @Test
    void humanOverrideStopsFixing() {
        when(store.get("ABC-1")).thenReturn(Optional.of(withUsage(run("ABC-1", RunState.FIXING, 101, 418), new RunUsage(0, 0, 0, 0, NOW), true)));
        assertThat(task.handleRequest(Map.of("ticketKey", "ABC-1"), null)).containsEntry("decision", "HUMAN_OVERRIDE");
    }
}
