package io.agentic.baseline;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

public final class ReportWriter {
    private final BaselineCalculator calculator = new BaselineCalculator();

    public String write(String jql, List<TicketMetrics> tickets, LocalDate generatedOn) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Baseline\n\n");
        sb.append("Generated ").append(generatedOn).append(" from JQL `").append(jql).append("`.\n\n");
        sb.append("Lead time = first transition to **In Progress** → PR ready for review. Review rounds = changes-requested reviews + 1. ")
                .append("Revert = a `Revert \"…\"` PR referencing the original within 14 days of merge.\n\n");
        sb.append("## Summary\n\n");
        sb.append(header());
        sb.append(row("All repos", calculator.summarise(tickets)));
        Map<String, List<TicketMetrics>> byRepo = tickets.stream()
                .collect(Collectors.groupingBy(t -> t.repo() == null ? "(no PR found)" : t.repo(), TreeMap::new, Collectors.toList()));
        byRepo.forEach((repo, list) -> sb.append(row(repo, calculator.summarise(list))));
        sb.append("\n## Pilot targets\n\n");
        sb.append("| KPI | Target |\n|---|---|\n");
        sb.append("| Lead time In Progress → PR ready | −40% vs baseline median |\n");
        sb.append("| Human review rounds per PR | ≤ 1.5 |\n");
        sb.append("| Reverts / defects within 14 days | ≤ baseline |\n");
        sb.append("| Merge rate of agent PRs | ≥ 60% |\n");
        return sb.toString();
    }

    private static String header() {
        return "| Scope | Tickets | Missing PR | Median lead time (h) | P75 lead time (h) | Median review rounds | Revert rate |\n"
                + "|---|---|---|---|---|---|---|\n";
    }

    private static String row(String scope, BaselineCalculator.Summary s) {
        return "| " + scope + " | " + s.count() + " | " + s.missingPr() + " | " + fmt(s.medianLeadTimeHours()) + " | "
                + fmt(s.p75LeadTimeHours()) + " | " + fmt(s.medianReviewRounds()) + " | " + pct(s.revertRate()) + " |\n";
    }

    private static String fmt(Double d) {
        return d == null ? "–" : String.format(Locale.ROOT, "%.1f", d);
    }

    private static String pct(Double d) {
        return d == null ? "–" : String.format(Locale.ROOT, "%.0f%%", d * 100);
    }
}
