package io.agentic.functions.tasks;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import io.agentic.core.run.Actor;
import io.agentic.core.run.RunState;
import io.agentic.core.text.Scrubber;
import io.agentic.functions.config.Services;
import io.agentic.functions.config.Wiring;
import io.agentic.functions.escalation.Diagnoser;
import io.agentic.functions.escalation.EscalationCard;
import io.agentic.functions.gates.GateFinding;
import io.agentic.functions.gates.GateFindings;
import io.agentic.functions.notify.Links;
import io.agentic.functions.notify.RunTransitions;
import io.agentic.functions.notify.SlackNotifier;
import io.agentic.functions.store.Run;
import io.agentic.functions.store.RunStore;
import io.agentic.integrations.github.GitHubClient;
import io.agentic.integrations.github.model.Commit;
import io.agentic.integrations.jira.JiraClient;
import io.agentic.integrations.jira.JiraTicket;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class EscalateTask implements RequestHandler<Map<String, Object>, Map<String, Object>> {
    private final RunStore store;
    private final RunTransitions transitions;
    private final JiraClient jira;
    private final GitHubClient github;
    private final SlackNotifier slack;
    private final Diagnoser diagnoser;
    private final Links links;

    public EscalateTask() {
        this(Services.instance().runStore(), Wiring.transitions(), Services.instance().jira(), Services.instance().github(),
                Wiring.slackNotifier(),
                new Diagnoser(Services.instance().textModel(), () -> Services.instance().params().find("/agentic/bedrock/textModelId").orElse("")),
                Wiring.links());
    }

    EscalateTask(RunStore store, RunTransitions transitions, JiraClient jira, GitHubClient github, SlackNotifier slack, Diagnoser diagnoser, Links links) {
        this.store = store;
        this.transitions = transitions;
        this.jira = jira;
        this.github = github;
        this.slack = slack;
        this.diagnoser = diagnoser;
        this.links = links;
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
        store.setEscalation(key, run.state() == RunState.ESCALATED && run.escalatedFrom() != null ? run.escalatedFrom() : run.state(), escalation);
        transitions.moveTo(key, RunState.ESCALATED, Actor.BOT, reason);

        Run escalated = store.get(key).orElseThrow();
        JiraTicket ticket = safeTicket(key);
        String diagnosis = diagnoser.diagnose(reason, findingMessages(key), commitMessages(escalated), ticket == null ? "" : ticket.acceptanceCriteria());
        escalation.put("diagnosis", diagnosis);
        List<Map<String, Object>> blocks = EscalationCard.blocks(escalated, reason, diagnosis, id, links);
        String fallback = EscalationCard.fallback(escalated, reason);
        String ts = slack.postBlocks(escalated, blocks, fallback);
        escalation.put("cardTs", ts);
        escalation.put("cardChannel", slack.channel());
        store.updateEscalation(key, escalation);
        if (ticket != null && ticket.assigneeEmail() != null) {
            try {
                slack.dmBlocks(ticket.assigneeEmail(), blocks, fallback);
            } catch (RuntimeException e) {
                System.err.println("WARN escalation DM failed for " + key + ": " + e.getMessage());
            }
        }
        return Map.of("escalationId", id);
    }

    private JiraTicket safeTicket(String key) {
        try {
            return jira.getTicket(key);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private List<String> findingMessages(String key) {
        return store.lastFindings(key, GateFindings.class)
                .map(f -> f.blocking().stream().limit(10).map(GateFinding::message).toList())
                .orElse(List.of());
    }

    private List<String> commitMessages(Run run) {
        if (run.prNumber() == null) {
            return List.of();
        }
        try {
            List<Commit> commits = github.listCommits(run.repo(), run.prNumber());
            return commits.subList(Math.max(0, commits.size() - 3), commits.size()).stream().map(Commit::message).toList();
        } catch (RuntimeException e) {
            return List.of();
        }
    }
}
