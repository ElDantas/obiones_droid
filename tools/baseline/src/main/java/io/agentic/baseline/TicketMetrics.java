package io.agentic.baseline;

import java.time.Duration;
import java.time.Instant;

public record TicketMetrics(
        String key,
        String repo,
        Double storyPoints,
        Instant inProgressAt,
        Instant prReadyAt,
        Instant mergedAt,
        int reviewRounds,
        boolean revertedWithin14d) {

    public boolean hasPr() {
        return prReadyAt != null;
    }

    public Double leadTimeHours() {
        if (inProgressAt == null || prReadyAt == null) {
            return null;
        }
        return Duration.between(inProgressAt, prReadyAt).toMinutes() / 60.0;
    }
}
