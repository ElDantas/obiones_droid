package io.agentic.functions.readiness;

import io.agentic.integrations.jira.JiraTicket;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public final class ReadinessChecker {
    private final RepoTargetParser parser = new RepoTargetParser();

    public List<String> check(JiraTicket ticket, Set<String> allowlist, int maxStoryPoints) {
        List<String> reasons = new ArrayList<>();
        RepoTargetParser.Result repo = parser.parse(ticket.targetRepo());
        repo.error().ifPresent(reasons::add);
        repo.repo().filter(r -> !allowlist.contains(r))
                .ifPresent(r -> reasons.add("Target repo " + r + " is not onboarded for agentic work"));
        if (ticket.acceptanceCriteria() == null || ticket.acceptanceCriteria().isBlank()) {
            reasons.add("No acceptance criteria found");
        }
        if (ticket.storyPoints() == null) {
            reasons.add("Story points are not set");
        } else if (ticket.storyPoints() > maxStoryPoints) {
            reasons.add("Story points " + format(ticket.storyPoints()) + " exceed the agentic limit of " + maxStoryPoints + "; split the ticket");
        }
        boolean multiRepo = Stream.concat(ticket.components().stream(), ticket.labels().stream())
                .anyMatch(v -> v.equalsIgnoreCase("multi-repo"));
        if (multiRepo) {
            reasons.add("Ticket spans multiple repos (component 'multi-repo')");
        }
        return reasons;
    }

    private static String format(double d) {
        return d == Math.rint(d) ? Long.toString((long) d) : Double.toString(d);
    }
}
