package io.agentic.functions.gates;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GateEvaluatorTest {
    private final GateEvaluator evaluator = new GateEvaluator();
    private final GateFinding finding = new GateFinding("ci", "ci:build", null, null, "failed");

    @Test
    void noPrEscalates() {
        assertThat(evaluator.decide(GateFindings.empty(null), false, false)).containsEntry("decision", "ESCALATE").containsEntry("reason", "No PR linked to the issue");
    }

    @Test
    void humanOverrideWins() {
        assertThat(evaluator.decide(new GateFindings("a", List.of(finding), List.of(), List.of()), true, true)).containsEntry("decision", "HUMAN_OVERRIDE");
    }

    @Test
    void scopeViolationBeatsBlocking() {
        assertThat(evaluator.decide(new GateFindings("a", List.of(finding), List.of(), List.of("Forbidden path changed: infra/x")), false, true))
                .containsEntry("decision", "ESCALATE").containsEntry("reason", "Forbidden path changed: infra/x");
    }

    @Test
    void blockingFails() {
        assertThat(evaluator.decide(new GateFindings("a", List.of(finding), List.of(), List.of()), false, true))
                .containsEntry("decision", "FAIL").containsEntry("reason", "1 blocking finding(s)");
    }

    @Test
    void cleanPasses() {
        assertThat(evaluator.decide(GateFindings.empty("a"), false, true)).containsEntry("decision", "PASS");
    }
}
