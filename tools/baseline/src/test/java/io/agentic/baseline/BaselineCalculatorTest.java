package io.agentic.baseline;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BaselineCalculatorTest {
    private static final Instant T0 = Instant.parse("2026-07-01T09:00:00Z");

    private static TicketMetrics ticket(String key, Integer hours, boolean reverted) {
        Instant ready = hours == null ? null : T0.plus(Duration.ofHours(hours));
        return new TicketMetrics(key, "acme/pay", 3.0, T0, ready, ready, 1, reverted);
    }

    @Test
    void percentilesUseLinearInterpolation() {
        List<Double> v = List.of(2.0, 4.0, 6.0, 8.0);
        assertThat(BaselineCalculator.percentile(v, 0.5)).isEqualTo(5.0);
        assertThat(BaselineCalculator.percentile(v, 0.75)).isEqualTo(6.5);
    }

    @Test
    void summaryComputesRevertRateAndMissingPr() {
        BaselineCalculator.Summary s = new BaselineCalculator().summarise(List.of(
                ticket("A-1", 2, true), ticket("A-2", 4, false), ticket("A-3", 6, false), ticket("A-4", 8, false), ticket("A-5", null, false)));
        assertThat(s.medianLeadTimeHours()).isEqualTo(5.0);
        assertThat(s.p75LeadTimeHours()).isEqualTo(6.5);
        assertThat(s.revertRate()).isEqualTo(0.25);
        assertThat(s.count()).isEqualTo(5);
        assertThat(s.missingPr()).isEqualTo(1);
    }

    @Test
    void emptyInputGivesNulls() {
        BaselineCalculator.Summary s = new BaselineCalculator().summarise(List.of());
        assertThat(s.medianLeadTimeHours()).isNull();
        assertThat(s.revertRate()).isNull();
    }
}
