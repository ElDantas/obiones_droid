package io.agentic.baseline;

import java.util.List;
import java.util.Objects;

public final class BaselineCalculator {

    public record Summary(Double medianLeadTimeHours, Double p75LeadTimeHours, Double medianReviewRounds, Double revertRate, int count, int missingPr) {
    }

    public Summary summarise(List<TicketMetrics> tickets) {
        List<TicketMetrics> withPr = tickets.stream().filter(TicketMetrics::hasPr).toList();
        List<Double> leadTimes = withPr.stream().map(TicketMetrics::leadTimeHours).filter(Objects::nonNull).sorted().toList();
        List<Double> rounds = withPr.stream().map(t -> (double) t.reviewRounds()).sorted().toList();
        Double revertRate = withPr.isEmpty() ? null : withPr.stream().filter(TicketMetrics::revertedWithin14d).count() / (double) withPr.size();
        return new Summary(
                percentile(leadTimes, 0.5),
                percentile(leadTimes, 0.75),
                percentile(rounds, 0.5),
                revertRate,
                tickets.size(),
                tickets.size() - withPr.size());
    }

    static Double percentile(List<Double> sorted, double p) {
        if (sorted.isEmpty()) {
            return null;
        }
        double index = (sorted.size() - 1) * p;
        int lower = (int) Math.floor(index);
        int upper = (int) Math.ceil(index);
        double fraction = index - lower;
        return sorted.get(lower) + (sorted.get(upper) - sorted.get(lower)) * fraction;
    }
}
