package io.agentic.functions.digest;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class DigestCalculator {

    public DigestData compute(String week, List<LedgerRow> rows, Map<String, List<String>> lessonActivity) {
        Map<String, List<LedgerRow>> byRun = rows.stream().collect(Collectors.groupingBy(LedgerRow::runId, LinkedHashMap::new, Collectors.toList()));
        int started = (int) rows.stream().filter(r -> "START".equals(r.from())).count();
        int merged = (int) rows.stream().filter(r -> "DONE".equals(r.to())).count();
        Set<String> escalatedRuns = rows.stream().filter(r -> "ESCALATED".equals(r.to())).map(LedgerRow::runId).collect(Collectors.toSet());
        Map<String, Integer> reasons = new HashMap<>();
        rows.stream().filter(r -> "ESCALATED".equals(r.to())).forEach(r -> reasons.merge(normalise(r.reason()), 1, Integer::sum));
        Map<String, Integer> sortedReasons = reasons.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));

        List<Double> leadTimes = new ArrayList<>();
        int premium = 0;
        int minutes = 0;
        for (List<LedgerRow> run : byRun.values()) {
            Instant start = run.stream().filter(r -> "START".equals(r.from())).map(LedgerRow::ts).min(Comparator.naturalOrder()).orElse(null);
            Instant review = run.stream().filter(r -> "HUMAN_REVIEW".equals(r.to())).map(LedgerRow::ts).min(Comparator.naturalOrder()).orElse(null);
            if (start != null && review != null) {
                leadTimes.add(Duration.between(start, review).toMinutes() / 60.0);
            }
            premium += run.stream().mapToInt(LedgerRow::premiumRequests).max().orElse(0);
            minutes += run.stream().mapToInt(LedgerRow::actionsMinutes).max().orElse(0);
        }
        return new DigestData(week, started, merged, escalatedRuns.size(), sortedReasons, median(leadTimes), premium, minutes,
                lessonActivity.getOrDefault("new", List.of()), lessonActivity.getOrDefault("promoted", List.of()),
                lessonActivity.getOrDefault("expired", List.of()));
    }

    static String normalise(String reason) {
        if (reason == null || reason.isBlank()) {
            return "Unknown";
        }
        int colon = reason.indexOf(':');
        return (colon > 0 ? reason.substring(0, colon) : reason).strip();
    }

    static Double median(List<Double> values) {
        if (values.isEmpty()) {
            return null;
        }
        List<Double> sorted = values.stream().sorted().toList();
        int n = sorted.size();
        return n % 2 == 1 ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
    }
}
