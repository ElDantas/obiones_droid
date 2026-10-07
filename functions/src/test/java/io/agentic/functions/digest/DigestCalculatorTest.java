package io.agentic.functions.digest;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DigestCalculatorTest {
    private static LedgerRow row(String run, String from, String to, String ts, int premium, String reason) {
        return new LedgerRow("K-" + run, run, "acme/payments", from, to, Instant.parse(ts), premium, premium * 10, reason);
    }

    @Test
    void countsRunsOutcomesAndLeadTime() {
        List<LedgerRow> rows = List.of(
                row("r1", "START", "READINESS", "2026-09-28T09:00:00Z", 0, "Run started"),
                row("r1", "GATES", "HUMAN_REVIEW", "2026-09-28T11:00:00Z", 3, "Ready"),
                row("r1", "HUMAN_REVIEW", "DONE", "2026-09-29T09:00:00Z", 3, "PR merged"),
                row("r2", "START", "READINESS", "2026-09-28T10:00:00Z", 0, "Run started"),
                row("r2", "FIXING", "ESCALATED", "2026-09-28T12:00:00Z", 5, "Same failure repeated: ci:build"),
                row("r3", "START", "READINESS", "2026-09-29T10:00:00Z", 0, "Run started"),
                row("r3", "CODING", "ABORTED", "2026-09-29T10:30:00Z", 1, "Label removed"));
        DigestData d = new DigestCalculator().compute("2026-W40", rows, Map.of("new", List.of("a → b")));
        assertThat(d.started()).isEqualTo(3);
        assertThat(d.merged()).isEqualTo(1);
        assertThat(d.escalated()).isEqualTo(1);
        assertThat(d.escalationReasons()).containsEntry("Same failure repeated", 1);
        assertThat(d.medianLeadTimeHours()).isEqualTo(2.0);
        assertThat(d.premiumRequests()).isEqualTo(9);
        assertThat(d.newLessons()).containsExactly("a → b");
    }
}
