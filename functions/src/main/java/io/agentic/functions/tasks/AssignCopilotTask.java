package io.agentic.functions.tasks;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import io.agentic.core.run.Actor;
import io.agentic.core.run.RunState;
import io.agentic.functions.config.Services;
import io.agentic.functions.config.Wiring;
import io.agentic.functions.notify.RunTransitions;
import io.agentic.functions.notify.SlackNotifier;
import io.agentic.functions.store.Run;
import io.agentic.functions.store.RunStore;
import io.agentic.functions.usage.UsageMeter;
import io.agentic.integrations.github.CopilotClient;
import io.agentic.integrations.github.CopilotUnavailableException;

import java.util.Map;

public class AssignCopilotTask implements RequestHandler<Map<String, Object>, Map<String, Object>> {
    private final RunStore store;
    private final RunTransitions transitions;
    private final CopilotClient copilot;
    private final UsageMeter usage;
    private final SlackNotifier slack;

    public AssignCopilotTask() {
        this(Services.instance().runStore(), Wiring.transitions(), Services.instance().copilot(),
                Wiring.usageMeter(), Wiring.slackNotifier());
    }

    AssignCopilotTask(RunStore store, RunTransitions transitions, CopilotClient copilot, UsageMeter usage, SlackNotifier slack) {
        this.store = store;
        this.transitions = transitions;
        this.copilot = copilot;
        this.usage = usage;
        this.slack = slack;
    }

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        String key = TaskSupport.ticketKey(input);
        Run run = store.get(key).orElseThrow();
        try {
            copilot.assignCopilot(run.repo(), run.issueNumber());
        } catch (CopilotUnavailableException e) {
            slack.post(run, "❌ " + e.getMessage());
            throw e;
        }
        transitions.moveTo(key, RunState.CODING, Actor.BOT, "Copilot assigned");
        usage.recordCopilotSession(key);
        return Map.of("assigned", true);
    }
}
