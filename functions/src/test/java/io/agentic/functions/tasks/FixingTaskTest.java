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
    private io.agentic.functions.ops.KillSwitchGuard killSwitch;

    @BeforeEach
    void setUp() {
        store = mock(RunStore.class);
        copilot = mock(CopilotClient.class);
        usage = mock(UsageMeter.class);
        RepoConfigLoader loader = mock(RepoConfigLoader.class);
        when(loader.load(any(), any())).thenReturn(new RepoConfig(Budgets.defaults(), List.of(), List.of(), List.of()));
        when(store.lastFindings("ABC-1", GateFindings.class)).thenReturn(Optional.of(GateFindings.empty("abc")));
        killSwitch = mock(io.agentic.functions.ops.KillSwitchGuard.class);
        when(killSwitch.check(anyString())).thenReturn(Optional.empty());
        task = new FixingTask(store, mock(RunTransitions.class), copilot, usage, loader, Clock.fixed(NOW, ZoneOffset.UTC), killSwitch);
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

    private static io.agentic.functions.store.Run withSnapshots(io.agentic.functions.store.Run r, List<io.agentic.core.loop.IterationSnapshot> snaps) {
        return new io.agentic.functions.store.Run(r.ticketKey(), r.runId(), r.repo(), r.state(), r.issueNumber(), r.prNumber(), r.executionArn(),
                r.slackThreadTs(), r.budgets(), r.usage(), snaps, r.humanOverride(), r.injectedLessonIds(), r.escalation(), r.escalatedFrom());
    }

    private static List<io.agentic.core.loop.IterationSnapshot> sameFailureTwice() {
        var a = new io.agentic.core.loop.IterationSnapshot(1, "h1", java.util.Set.of("ci:build:OrderServiceTest.discount_rounding"), java.util.Set.of("src/Order.java"), java.util.Set.of("src/Order.java"), Map.of());
        var b = new io.agentic.core.loop.IterationSnapshot(2, "h1", java.util.Set.of("ci:build:OrderServiceTest.discount_rounding"), java.util.Set.of("src/Order.java"), java.util.Set.of("src/Order.java"), Map.of());
        return List.of(a, b);
    }

    @Test
    void repeatedFingerprintEscalatesWithoutComment() {
        when(store.get("ABC-1")).thenReturn(Optional.of(withSnapshots(withUsage(run("ABC-1", RunState.FIXING, 101, 418), new RunUsage(1, 0, 5, 10, NOW), false), sameFailureTwice())));
        Map<String, Object> out = task.handleRequest(Map.of("ticketKey", "ABC-1"), null);
        assertThat(out).containsEntry("decision", "ESCALATE");
        assertThat((String) out.get("reason")).startsWith("Same failure repeated");
        verify(copilot, never()).instruct(anyString(), anyInt(), anyString());
    }

    @Test
    void loopOverrideSkipsDetectionOnceAndClearsFlag() {
        when(store.get("ABC-1")).thenReturn(Optional.of(withSnapshots(withUsage(run("ABC-1", RunState.FIXING, 101, 418), new RunUsage(1, 0, 5, 10, NOW), false), sameFailureTwice())));
        when(store.flag("ABC-1", "loopOverride")).thenReturn(true);
        assertThat(task.handleRequest(Map.of("ticketKey", "ABC-1"), null)).containsEntry("decision", "CONTINUE");
        verify(store).setFlag("ABC-1", "loopOverride", false);
    }

    @Test
    void softPremiumWarnsOnce() {
        when(store.get("ABC-1")).thenReturn(Optional.of(withUsage(run("ABC-1", RunState.FIXING, 101, 418), new RunUsage(1, 0, 30, 10, NOW), false)));
        task.handleRequest(Map.of("ticketKey", "ABC-1"), null);
        verify(usage).warnOnce(eq("ABC-1"), eq("budget-warn"), org.mockito.ArgumentMatchers.startsWith("⚠️ Approaching limits: Premium requests 30/50"));
    }

    @Test
    void killSwitchEscalates() {
        when(store.get("ABC-1")).thenReturn(Optional.of(withUsage(run("ABC-1", RunState.FIXING, 101, 418), new RunUsage(1, 0, 5, 10, NOW), false)));
        when(killSwitch.check("acme/payments")).thenReturn(Optional.of("Paused by kill switch (global)"));
        assertThat(task.handleRequest(Map.of("ticketKey", "ABC-1"), null)).containsEntry("decision", "ESCALATE").containsEntry("reason", "Paused by kill switch (global)");
        verify(copilot, never()).instruct(anyString(), anyInt(), anyString());
    }

    @Test
    void humanOverrideStopsFixing() {
        when(store.get("ABC-1")).thenReturn(Optional.of(withUsage(run("ABC-1", RunState.FIXING, 101, 418), new RunUsage(0, 0, 0, 0, NOW), true)));
        assertThat(task.handleRequest(Map.of("ticketKey", "ABC-1"), null)).containsEntry("decision", "HUMAN_OVERRIDE");
    }
}
