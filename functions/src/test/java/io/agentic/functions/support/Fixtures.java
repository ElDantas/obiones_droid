package io.agentic.functions.support;

import com.fasterxml.jackson.databind.JsonNode;
import io.agentic.core.budget.Budgets;
import io.agentic.core.budget.RunUsage;
import io.agentic.core.run.RunState;
import io.agentic.functions.store.Run;
import io.agentic.integrations.http.Json;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class Fixtures {
    private Fixtures() {
    }

    public static String text(String path) {
        try (InputStream in = Fixtures.class.getResourceAsStream("/fixtures/" + path)) {
            if (in == null) {
                throw new IllegalArgumentException("Missing fixture " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static JsonNode json(String path) {
        try {
            return Json.MAPPER.readTree(text(path));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static Run run(String key, RunState state, Integer issue, Integer pr) {
        return run(key, state, issue, pr, null);
    }

    public static Run run(String key, RunState state, Integer issue, Integer pr, RunState escalatedFrom) {
        return new Run(key, "r1", "acme/payments", state, issue, pr, "arn:exec", null, Budgets.defaults(),
                RunUsage.start(Instant.parse("2026-10-07T12:00:00Z")), List.of(), false, List.of(), Map.of(), escalatedFrom);
    }

    public static Run withUsage(Run r, RunUsage usage, boolean humanOverride) {
        return new Run(r.ticketKey(), r.runId(), r.repo(), r.state(), r.issueNumber(), r.prNumber(), r.executionArn(), r.slackThreadTs(),
                r.budgets(), usage, r.snapshots(), humanOverride, r.injectedLessonIds(), r.escalation(), r.escalatedFrom());
    }
}
