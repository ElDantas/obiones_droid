package io.agentic.functions.notify;

import io.agentic.core.run.RunState;
import io.agentic.functions.store.Run;
import io.agentic.functions.store.RunStore;
import io.agentic.integrations.jira.JiraClient;
import io.agentic.integrations.jira.JiraTicket;
import io.agentic.integrations.slack.SlackClient;

import java.util.function.Supplier;

public class SlackNotifier implements Notifier {
    private final SlackClient slack;
    private final JiraClient jira;
    private final RunStore store;
    private final Supplier<String> channel;
    private final Links links;

    public SlackNotifier(SlackClient slack, JiraClient jira, RunStore store, Supplier<String> channel, Links links) {
        this.slack = slack;
        this.jira = jira;
        this.store = store;
        this.channel = channel;
        this.links = links;
    }

    @Override
    public void onTransition(Run run, RunState from, RunState to, String reason) {
        Messages.slack(run, to, reason, links).ifPresent(text -> post(run, text));
        if (to == RunState.NEEDS_INFO) {
            JiraTicket t = jira.getTicket(run.ticketKey());
            if (t.reporterEmail() != null) {
                slack.dmByEmail(t.reporterEmail(), null, Messages.slack(run, to, reason, links).orElse("") + "\n" + links.ticket(run.ticketKey()));
            }
        }
    }

    public void post(Run run, String text) {
        String thread = ensureRoot(run);
        slack.post(channel.get(), thread, null, text);
    }

    public String postBlocks(Run run, java.util.List<java.util.Map<String, Object>> blocks, String fallback) {
        String thread = ensureRoot(run);
        return slack.post(channel.get(), thread, blocks, fallback);
    }

    public void dmBlocks(String email, java.util.List<java.util.Map<String, Object>> blocks, String fallback) {
        if (email != null) {
            slack.dmByEmail(email, blocks, fallback);
        }
    }

    public String channel() {
        return channel.get();
    }

    public void dm(String email, String text) {
        if (email != null) {
            slack.dmByEmail(email, null, text);
        }
    }

    String ensureRoot(Run run) {
        if (run.slackThreadTs() != null) {
            return run.slackThreadTs();
        }
        String summary;
        try {
            summary = jira.getTicket(run.ticketKey()).summary();
        } catch (RuntimeException e) {
            summary = "";
        }
        String ts = slack.post(channel.get(), null, null, Messages.root(run, summary, links));
        store.setSlackThread(run.ticketKey(), ts);
        return ts;
    }
}
