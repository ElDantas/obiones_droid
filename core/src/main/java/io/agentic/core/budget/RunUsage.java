package io.agentic.core.budget;

import java.time.Instant;

public record RunUsage(int gateIterations, int humanIterations, int premiumRequests, int actionsMinutes, Instant startedAt) {
    public static RunUsage start(Instant now) {
        return new RunUsage(0, 0, 0, 0, now);
    }
}
