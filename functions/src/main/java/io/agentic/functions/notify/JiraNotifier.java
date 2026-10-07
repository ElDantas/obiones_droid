package io.agentic.functions.notify;

import io.agentic.core.run.RunState;
import io.agentic.functions.store.Run;
import io.agentic.integrations.jira.JiraClient;
import io.agentic.integrations.jira.JiraTransitionMissing;
import io.agentic.integrations.slack.SlackClient;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class JiraNotifier implements Notifier {
    private final JiraClient jira;
    private final SlackClient slack;
    private final Supplier<String> opsChannel;
    private final Supplier<String> agentReadyUrl;
    private final Links links;
    private final Set<String> warned = ConcurrentHashMap.newKeySet();

    public JiraNotifier(JiraClient jira, SlackClient slack, Supplier<String> opsChannel, Supplier<String> agentReadyUrl, Links links) {
        this.jira = jira;
        this.slack = slack;
        this.opsChannel = opsChannel;
        this.agentReadyUrl = agentReadyUrl;
        this.links = links;
    }

    @Override
    public void onTransition(Run run, RunState from, RunState to, String reason) {
        JiraStatusMap.statusFor(from, to).ifPresent(status -> transition(run.ticketKey(), status));
        Messages.jira(run, to, reason, links, agentReadyUrl.get()).ifPresent(text -> jira.comment(run.ticketKey(), text));
    }

    private void transition(String key, String status) {
        try {
            jira.transition(key, status);
        } catch (JiraTransitionMissing e) {
            System.err.println("WARN " + e.getMessage());
            if (warned.add(key)) {
                try {
                    slack.post(opsChannel.get(), null, null, "⚠️ Jira workflow for " + key + " has no transition to '" + status + "'. Check the pilot project's workflow.");
                } catch (RuntimeException ignored) {
                    System.err.println("Ops post failed for " + key);
                }
            }
        }
    }
}
