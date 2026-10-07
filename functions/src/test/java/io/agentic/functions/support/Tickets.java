package io.agentic.functions.support;

import io.agentic.integrations.jira.JiraTicket;

import java.util.List;

public final class Tickets {
    private Tickets() {
    }

    public static JiraTicket ready() {
        return new JiraTicket("ABC-1", "Cap discounts", "Discounts stack too far.", "- Cap at 50%\n- Tax after cap",
                "Acme/Payments", 3.0, List.of("orders"), List.of("Agentic AI Approved"), "rep@corp.com", "dev@corp.com",
                List.of("https://corp.atlassian.net/wiki/spaces/ENG/pages/1"));
    }

    public static JiraTicket with(String targetRepo, String ac, Double points, List<String> components) {
        JiraTicket r = ready();
        return new JiraTicket(r.key(), r.summary(), r.description(), ac, targetRepo, points, components, r.labels(),
                r.reporterEmail(), r.assigneeEmail(), r.confluenceLinks());
    }
}
