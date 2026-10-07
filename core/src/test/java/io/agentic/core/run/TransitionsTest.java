package io.agentic.core.run;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransitionsTest {

    @ParameterizedTest
    @CsvSource({
            "READINESS,CONTEXT", "READINESS,NEEDS_INFO", "READINESS,ESCALATED", "READINESS,ABORTED",
            "CONTEXT,CODING", "CONTEXT,ESCALATED", "CONTEXT,ABORTED",
            "CODING,GATES", "CODING,HUMAN_REVIEW", "CODING,ESCALATED", "CODING,ABORTED",
            "GATES,HUMAN_REVIEW", "GATES,FIXING", "GATES,ESCALATED", "GATES,ABORTED",
            "FIXING,GATES", "FIXING,HUMAN_REVIEW", "FIXING,ESCALATED", "FIXING,ABORTED",
            "HUMAN_REVIEW,HUMAN_FIX", "HUMAN_REVIEW,DONE", "HUMAN_REVIEW,ABORTED",
            "HUMAN_FIX,GATES", "HUMAN_FIX,HUMAN_REVIEW", "HUMAN_FIX,ESCALATED", "HUMAN_FIX,ABORTED",
            "ESCALATED,CODING", "ESCALATED,GATES", "ESCALATED,FIXING", "ESCALATED,HUMAN_FIX",
            "ESCALATED,HUMAN_REVIEW", "ESCALATED,ABORTED"
    })
    void allowedTransitionsPass(RunState from, RunState to) {
        assertThat(Transitions.isAllowed(from, to)).isTrue();
        Transitions.check(from, to);
    }

    @ParameterizedTest
    @CsvSource({"DONE,GATES", "NEEDS_INFO,CONTEXT", "HUMAN_REVIEW,FIXING", "ABORTED,READINESS"})
    void illegalTransitionsThrow(RunState from, RunState to) {
        assertThatThrownBy(() -> Transitions.check(from, to))
                .isInstanceOf(IllegalTransitionException.class)
                .hasMessageContaining(from + " -> " + to);
    }

    @Test
    void needsInfoIsNotActive() {
        assertThat(RunState.NEEDS_INFO.isActive()).isFalse();
        assertThat(RunState.DONE.isTerminal()).isTrue();
        assertThat(RunState.ABORTED.isTerminal()).isTrue();
        assertThat(RunState.GATES.isActive()).isTrue();
    }
}
