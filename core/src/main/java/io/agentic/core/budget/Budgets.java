package io.agentic.core.budget;

import java.time.Duration;
import java.util.List;
import java.util.Map;

public record Budgets(
        int maxGateIterations,
        int maxHumanIterations,
        int premiumSoft,
        int premiumHard,
        Duration codingTimeout,
        Duration maxRunAge,
        int actionsMinutesSoft,
        int actionsMinutesHard,
        int maxDiffLines,
        int maxDiffFiles,
        List<String> forbiddenPaths) {

    public static Budgets defaults() {
        return new Budgets(3, 3, 30, 50, Duration.ofMinutes(60), Duration.ofDays(3), 120, 240, 800, 25,
                List.of("infra/**", "**/migrations/**", "**/*.pem", "**/*.key", "**/.env*", ".github/workflows/**"));
    }

    public Budgets raisedBy(double factor) {
        return new Budgets(
                (int) Math.ceil(maxGateIterations * factor),
                (int) Math.ceil(maxHumanIterations * factor),
                (int) Math.ceil(premiumSoft * factor),
                (int) Math.ceil(premiumHard * factor),
                codingTimeout,
                maxRunAge,
                (int) Math.ceil(actionsMinutesSoft * factor),
                (int) Math.ceil(actionsMinutesHard * factor),
                maxDiffLines,
                maxDiffFiles,
                forbiddenPaths);
    }

    @SuppressWarnings("unchecked")
    public Budgets merge(Map<String, Object> o) {
        return new Budgets(
                intOr(o, "maxGateIterations", maxGateIterations),
                intOr(o, "maxHumanIterations", maxHumanIterations),
                intOr(o, "premiumSoft", premiumSoft),
                intOr(o, "premiumHard", premiumHard),
                o.containsKey("codingTimeoutMinutes") ? Duration.ofMinutes(intOr(o, "codingTimeoutMinutes", 0)) : codingTimeout,
                o.containsKey("maxRunAgeHours") ? Duration.ofHours(intOr(o, "maxRunAgeHours", 0)) : maxRunAge,
                intOr(o, "actionsMinutesSoft", actionsMinutesSoft),
                intOr(o, "actionsMinutesHard", actionsMinutesHard),
                intOr(o, "maxDiffLines", maxDiffLines),
                intOr(o, "maxDiffFiles", maxDiffFiles),
                (List<String>) o.getOrDefault("forbiddenPaths", forbiddenPaths));
    }

    private static int intOr(Map<String, Object> o, String key, int fallback) {
        Object v = o.get(key);
        return v instanceof Number n ? n.intValue() : fallback;
    }
}
