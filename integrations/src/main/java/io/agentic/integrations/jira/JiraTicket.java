package io.agentic.integrations.jira;

import java.util.List;

public record JiraTicket(
        String key,
        String summary,
        String description,
        String acceptanceCriteria,
        String targetRepo,
        Double storyPoints,
        List<String> components,
        List<String> labels,
        String reporterEmail,
        String assigneeEmail,
        List<String> confluenceLinks) {
}
