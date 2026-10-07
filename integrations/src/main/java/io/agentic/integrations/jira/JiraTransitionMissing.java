package io.agentic.integrations.jira;

public class JiraTransitionMissing extends RuntimeException {
    public JiraTransitionMissing(String key, String statusName) {
        super("No transition to '" + statusName + "' available for " + key);
    }
}
