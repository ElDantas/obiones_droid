package io.agentic.functions.escalation;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.agentic.core.text.Scrubber;
import io.agentic.functions.notify.Links;
import io.agentic.functions.store.Run;
import io.agentic.integrations.http.Json;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class EscalationCard {
    public static final List<String> DECISIONS = List.of("RESUME", "RAISE_BUDGET", "TAKE_OVER", "ABORT");
    private static final Map<String, String> LABELS = Map.of(
            "RESUME", "Resume", "RAISE_BUDGET", "Raise budget +50%", "TAKE_OVER", "Take over", "ABORT", "Abort");

    private EscalationCard() {
    }

    public static String fallback(Run run, String reason) {
        return "⚠️ " + run.ticketKey() + " paused: " + Scrubber.scrub(reason);
    }

    public static List<Map<String, Object>> blocks(Run run, String reason, String diagnosis, String escalationId, Links links) {
        String usage = "Iterations " + run.usage().gateIterations() + "/" + run.budgets().maxGateIterations()
                + " · Premium requests " + run.usage().premiumRequests() + "/" + run.budgets().premiumHard()
                + " · Actions " + run.usage().actionsMinutes() + "/" + run.budgets().actionsMinutesHard() + " min"
                + (run.prNumber() == null ? "" : " · <" + links.pr(run.repo(), run.prNumber()) + "|PR #" + run.prNumber() + ">");
        List<Map<String, Object>> buttons = DECISIONS.stream().map(d -> button(run.ticketKey(), escalationId, d)).toList();
        return List.of(
                section("⚠️ *" + run.ticketKey() + " paused:* " + Scrubber.scrub(reason)),
                Map.of("type", "context", "elements", List.of(Map.of("type", "mrkdwn", "text", usage))),
                section("*Diagnosis:* " + Scrubber.scrub(diagnosis)),
                Map.of("type", "actions", "block_id", "escalation", "elements", buttons),
                Map.of("type", "context", "elements", List.of(Map.of("type", "mrkdwn", "text", "Auto-aborts in 24h if nobody acts."))));
    }

    public static List<Map<String, Object>> resolved(List<Map<String, Object>> original, String decision, String userId) {
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        for (Map<String, Object> b : original) {
            if ("actions".equals(b.get("type"))) {
                out.add(Map.of("type", "context", "elements", List.of(Map.of("type", "mrkdwn",
                        "text", "✅ " + LABELS.getOrDefault(decision, decision) + " by <@" + userId + ">"))));
            } else {
                out.add(b);
            }
        }
        return out;
    }

    private static Map<String, Object> section(String text) {
        return Map.of("type", "section", "text", Map.of("type", "mrkdwn", "text", text));
    }

    private static Map<String, Object> button(String ticketKey, String escalationId, String decision) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("type", "button");
        b.put("action_id", "escalation_" + decision.toLowerCase());
        b.put("text", Map.of("type", "plain_text", "text", LABELS.get(decision)));
        b.put("value", value(ticketKey, escalationId, decision));
        if (decision.equals("ABORT")) {
            b.put("style", "danger");
            b.put("confirm", Map.of(
                    "title", Map.of("type", "plain_text", "text", "Abort " + ticketKey + "?"),
                    "text", Map.of("type", "plain_text", "text", "The agent PR will be closed and the run ends."),
                    "confirm", Map.of("type", "plain_text", "text", "Abort"),
                    "deny", Map.of("type", "plain_text", "text", "Cancel")));
        } else if (decision.equals("RESUME")) {
            b.put("style", "primary");
        }
        return b;
    }

    private static String value(String ticketKey, String escalationId, String decision) {
        try {
            return Json.MAPPER.writeValueAsString(Map.of("ticketKey", ticketKey, "escalationId", escalationId, "decision", decision));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
