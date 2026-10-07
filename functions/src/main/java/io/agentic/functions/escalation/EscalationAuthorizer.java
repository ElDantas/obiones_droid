package io.agentic.functions.escalation;

import io.agentic.integrations.slack.SlackClient;

import java.util.List;
import java.util.Locale;

public class EscalationAuthorizer {
    public static final String APPROVER_GROUP = "agentic-approvers";

    private final SlackClient slack;

    public EscalationAuthorizer(SlackClient slack) {
        this.slack = slack;
    }

    public boolean isAllowed(String slackUserId, String assigneeEmail, List<String> approverEmails) {
        try {
            String email = slack.lookupEmail(slackUserId);
            if (email != null) {
                String e = email.toLowerCase(Locale.ROOT);
                if (assigneeEmail != null && e.equals(assigneeEmail.toLowerCase(Locale.ROOT))) {
                    return true;
                }
                if (approverEmails.stream().anyMatch(a -> a.equalsIgnoreCase(e))) {
                    return true;
                }
            }
            return slack.usergroupMemberIds(APPROVER_GROUP).contains(slackUserId);
        } catch (RuntimeException ex) {
            System.err.println("WARN authorisation lookup failed for " + slackUserId + ": " + ex.getMessage());
            return false;
        }
    }
}
