package io.agentic.functions.gates;

import io.agentic.integrations.http.Json;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VerdictParser {
    private static final Pattern BLOCK = Pattern.compile("```agentic-verdict\\s*(\\{.*?})\\s*```", Pattern.DOTALL);

    public record Finding(String id, String file, Integer line, String message) {
    }

    public record Verdict(String gate, boolean pass, String summary, List<Finding> findings, Integer premiumRequests) {
        public Verdict {
            findings = findings == null ? List.of() : findings;
            premiumRequests = premiumRequests == null ? 0 : premiumRequests;
        }
    }

    private VerdictParser() {
    }

    public static Optional<Verdict> parse(String outputText) {
        if (outputText == null) {
            return Optional.empty();
        }
        Matcher m = BLOCK.matcher(outputText);
        if (!m.find()) {
            return Optional.empty();
        }
        try {
            Verdict v = Json.MAPPER.readValue(m.group(1), Verdict.class);
            return v.gate() == null ? Optional.empty() : Optional.of(v);
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
