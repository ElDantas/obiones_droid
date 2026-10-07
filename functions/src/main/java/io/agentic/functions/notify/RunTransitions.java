package io.agentic.functions.notify;

import io.agentic.core.run.Actor;
import io.agentic.core.run.RunState;
import io.agentic.functions.store.Run;
import io.agentic.functions.store.RunStore;

import java.util.List;

public class RunTransitions {
    private final RunStore store;
    private final List<Notifier> notifiers;

    public RunTransitions(RunStore store, List<Notifier> notifiers) {
        this.store = store;
        this.notifiers = notifiers;
    }

    public RunState moveTo(String ticketKey, RunState to, Actor actor, String reason) {
        Run before = store.get(ticketKey).orElseThrow(() -> new IllegalStateException("No run for " + ticketKey));
        RunState from = before.state();
        store.moveTo(ticketKey, to, actor, reason);
        if (from != to) {
            Run after = store.get(ticketKey).orElseThrow();
            for (Notifier n : notifiers) {
                try {
                    n.onTransition(after, from, to, reason);
                } catch (RuntimeException e) {
                    System.err.println("Notifier " + n.getClass().getSimpleName() + " failed for " + ticketKey + " " + from + "->" + to + ": " + e);
                }
            }
        }
        return to;
    }
}
