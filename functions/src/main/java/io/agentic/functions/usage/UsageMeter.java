package io.agentic.functions.usage;

import io.agentic.core.budget.RunUsage;
import io.agentic.functions.metrics.Metrics;
import io.agentic.functions.notify.SlackNotifier;
import io.agentic.functions.store.RunStore;

import java.util.function.Supplier;

public class UsageMeter {
    private final RunStore store;
    private final Supplier<SlackNotifier> slack;

    public UsageMeter(RunStore store) {
        this(store, () -> null);
    }

    public UsageMeter(RunStore store, Supplier<SlackNotifier> slack) {
        this.store = store;
        this.slack = slack;
    }

    public RunUsage recordCopilotSession(String ticketKey) {
        Metrics.add("PremiumRequests", "copilot", 1);
        return store.updateUsage(ticketKey, u -> new RunUsage(u.gateIterations(), u.humanIterations(), u.premiumRequests() + 1, u.actionsMinutes(), u.startedAt()));
    }

    public RunUsage recordGateIteration(String ticketKey) {
        return store.updateUsage(ticketKey, u -> new RunUsage(u.gateIterations() + 1, u.humanIterations(), u.premiumRequests(), u.actionsMinutes(), u.startedAt()));
    }

    public RunUsage recordHumanIteration(String ticketKey) {
        return store.updateUsage(ticketKey, u -> new RunUsage(u.gateIterations(), u.humanIterations() + 1, u.premiumRequests(), u.actionsMinutes(), u.startedAt()));
    }

    public RunUsage recordActionsMinutes(String ticketKey, String workflowRunId, int minutes) {
        if (minutes <= 0 || !store.addToSet(ticketKey, "processedWorkflowRuns", workflowRunId)) {
            return store.get(ticketKey).orElseThrow().usage();
        }
        return store.updateUsage(ticketKey, u -> new RunUsage(u.gateIterations(), u.humanIterations(), u.premiumRequests(), u.actionsMinutes() + minutes, u.startedAt()));
    }

    public RunUsage recordGateAgentRequests(String ticketKey, String checkRunId, int requests) {
        if (requests <= 0 || !store.addToSet(ticketKey, "processedCheckRuns", checkRunId)) {
            return store.get(ticketKey).orElseThrow().usage();
        }
        Metrics.add("PremiumRequests", "gate-agent", requests);
        return store.updateUsage(ticketKey, u -> new RunUsage(u.gateIterations(), u.humanIterations(), u.premiumRequests() + requests, u.actionsMinutes(), u.startedAt()));
    }

    public boolean warnOnce(String ticketKey, String key, String text) {
        if (!store.addToSet(ticketKey, "warningsSent", key)) {
            return false;
        }
        SlackNotifier s = slack.get();
        if (s != null) {
            s.post(store.get(ticketKey).orElseThrow(), text);
        }
        return true;
    }
}
