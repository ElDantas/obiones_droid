package io.agentic.core.run;

import java.time.Instant;

public record LedgerEvent(
        String ticketKey,
        String runId,
        String repo,
        RunState from,
        RunState to,
        Instant ts,
        Actor actor,
        int iteration,
        int premiumRequests,
        int actionsMinutes,
        String reason) {
}
