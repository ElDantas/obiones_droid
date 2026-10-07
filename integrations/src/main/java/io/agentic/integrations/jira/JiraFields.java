package io.agentic.integrations.jira;

public record JiraFields(String targetRepo, String acceptanceCriteria, String storyPoints) {
    public boolean hasAcceptanceCriteriaField() {
        return acceptanceCriteria != null && !acceptanceCriteria.isBlank() && !"none".equalsIgnoreCase(acceptanceCriteria);
    }
}
