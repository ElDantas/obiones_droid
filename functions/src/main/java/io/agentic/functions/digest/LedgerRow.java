package io.agentic.functions.digest;

import java.time.Instant;

public record LedgerRow(String ticketKey, String runId, String repo, String from, String to, Instant ts, int premiumRequests, int actionsMinutes, String reason) {
}
