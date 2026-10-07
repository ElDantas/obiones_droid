package io.agentic.functions.usage;

import io.agentic.core.budget.RunUsage;
import io.agentic.functions.store.Run;
import io.agentic.functions.store.RunStore;

import java.util.function.UnaryOperator;

public class UsageMeter {
    private final RunStore store;

    public UsageMeter(RunStore store) {
        this.store = store;
    }

    public RunUsage recordCopilotSession(String ticketKey) {
        return update(ticketKey, u -> new RunUsage(u.gateIterations(), u.humanIterations(), u.premiumRequests() + 1, u.actionsMinutes(), u.startedAt()));
    }

    public RunUsage recordGateIteration(String ticketKey) {
        return update(ticketKey, u -> new RunUsage(u.gateIterations() + 1, u.humanIterations(), u.premiumRequests(), u.actionsMinutes(), u.startedAt()));
    }

    public RunUsage recordHumanIteration(String ticketKey) {
        return update(ticketKey, u -> new RunUsage(u.gateIterations(), u.humanIterations() + 1, u.premiumRequests(), u.actionsMinutes(), u.startedAt()));
    }

    private RunUsage update(String ticketKey, UnaryOperator<RunUsage> change) {
        Run run = store.get(ticketKey).orElseThrow();
        RunUsage next = change.apply(run.usage());
        store.saveUsage(ticketKey, next);
        return next;
    }
}
