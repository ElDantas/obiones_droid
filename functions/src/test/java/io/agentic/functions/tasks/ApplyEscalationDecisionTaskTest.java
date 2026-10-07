package io.agentic.functions.tasks;

import io.agentic.core.budget.Budgets;
import io.agentic.core.run.Actor;
import io.agentic.core.run.RunState;
import io.agentic.functions.notify.RunTransitions;
import io.agentic.functions.store.RunStore;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static io.agentic.functions.support.Fixtures.run;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApplyEscalationDecisionTaskTest {
    private final RunStore store = mock(RunStore.class);
    private final RunTransitions transitions = mock(RunTransitions.class);
    private final ApplyEscalationDecisionTask task = new ApplyEscalationDecisionTask(store, transitions);

    private Map<String, Object> input(String decision) {
        return Map.of("ticketKey", "ABC-1", "signal", Map.of("decision", decision, "by", "U1"));
    }

    @Test
    void resumeFromCodingWaitsForPrAgain() {
        when(store.get("ABC-1")).thenReturn(Optional.of(run("ABC-1", RunState.ESCALATED, 101, null, RunState.CODING)));
        assertThat(task.handleRequest(input("RESUME"), null)).containsEntry("decision", "RESUME_CODING");
        verify(transitions).moveTo("ABC-1", RunState.CODING, Actor.HUMAN, "Escalation resolved: RESUME");
    }

    @Test
    void resumeFromGatesReevaluates() {
        when(store.get("ABC-1")).thenReturn(Optional.of(run("ABC-1", RunState.ESCALATED, 101, 418, RunState.GATES)));
        assertThat(task.handleRequest(input("RESUME"), null)).containsEntry("decision", "RESUME_GATES");
    }

    @Test
    void raiseBudgetMultipliesBudgets() {
        when(store.get("ABC-1")).thenReturn(Optional.of(run("ABC-1", RunState.ESCALATED, 101, 418, RunState.FIXING)));
        assertThat(task.handleRequest(input("RAISE_BUDGET"), null)).containsEntry("decision", "RESUME_FIXING");
        verify(store).saveBudgets("ABC-1", Budgets.defaults().raisedBy(1.5));
        verify(store).setFlag("ABC-1", "loopOverride", true);
    }

    @Test
    void takeOverDoesNotMoveState() {
        when(store.get("ABC-1")).thenReturn(Optional.of(run("ABC-1", RunState.ESCALATED, 101, 418, RunState.FIXING)));
        assertThat(task.handleRequest(input("TAKE_OVER"), null)).containsEntry("decision", "TAKE_OVER");
        verify(transitions, never()).moveTo(anyString(), any(), any(), anyString());
    }

    @Test
    void resumeBeforeCodingAborts() {
        when(store.get("ABC-1")).thenReturn(Optional.of(run("ABC-1", RunState.ESCALATED, null, null, RunState.CONTEXT)));
        Map<String, Object> out = task.handleRequest(input("RESUME"), null);
        assertThat(out).containsEntry("decision", "ABORT");
        assertThat((String) out.get("reason")).contains("/agent restart");
    }
}
