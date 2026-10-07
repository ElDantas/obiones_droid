package io.agentic.functions.tasks;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import io.agentic.core.run.Actor;
import io.agentic.core.run.RunState;
import io.agentic.core.text.Scrubber;
import io.agentic.functions.config.Services;
import io.agentic.functions.config.Wiring;
import io.agentic.functions.notify.RunTransitions;
import io.agentic.functions.notify.SlackNotifier;
import io.agentic.functions.store.Run;
import io.agentic.functions.store.RunStore;
import io.agentic.integrations.jira.JiraClient;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class EscalateTask implements RequestHandler<Map<String, Object>, Map<String, Object>> {
    private final RunStore store;
    private final RunTransitions transitions;
    private final JiraClient jira;
    private final SlackNotifier slack;

    public EscalateTask() {
        this(Services.instance().runStore(), Wiring.transitions(), Services.instance().jira(), Wiring.slackNotifier());
    }

    EscalateTask(RunStore store, RunTransitions transitions, JiraClient jira, SlackNotifier slack) {
        this.store = store;
        this.transitions = transitions;
        this.jira = jira;
        this.slack = slack;
    }

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        String key = TaskSupport.ticketKey(input);
        String reason = Scrubber.scrub(TaskSupport.string(input, "escalationReason", "Escalated"));
        Run run = store.get(key).orElseThrow();
        String id = UUID.randomUUID().toString();
        Map<String, Object> escalation = new LinkedHashMap<>();
        escalation.put("id", id);
        escalation.put("reason", reason);
        if (run.state() != RunState.ESCALATED) {
            store.setEscalation(key, run.state(), escalation);
        }
        transitions.moveTo(key, RunState.ESCALATED, Actor.BOT, reason);
        try {
            String assignee = jira.getTicket(key).assigneeEmail();
            slack.dm(assignee, "⚠️ " + key + " paused: " + reason + "\nTo abort, remove the `Agentic AI Approved` label. To retry from scratch, comment `/agent restart` on the Jira ticket.");
        } catch (RuntimeException e) {
            System.err.println("WARN escalation DM failed for " + key + ": " + e.getMessage());
        }
        return Map.of("escalationId", id);
    }
}
