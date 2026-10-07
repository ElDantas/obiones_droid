package io.agentic.core.budget;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class BudgetEvaluator {

    public enum IterationKind { GATE, HUMAN, NONE }

    public BudgetVerdict evaluate(Budgets b, RunUsage u, Instant now, IterationKind kind) {
        List<String> breaches = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (kind == IterationKind.GATE && u.gateIterations() >= b.maxGateIterations()) {
            breaches.add("Gate fix iterations " + u.gateIterations() + "/" + b.maxGateIterations());
        }
        if (kind == IterationKind.HUMAN && u.humanIterations() >= b.maxHumanIterations()) {
            breaches.add("Human fix iterations " + u.humanIterations() + "/" + b.maxHumanIterations());
        }
        if (u.premiumRequests() >= b.premiumHard()) {
            breaches.add("Premium requests " + u.premiumRequests() + "/" + b.premiumHard());
        } else if (u.premiumRequests() >= b.premiumSoft()) {
            warnings.add("Premium requests " + u.premiumRequests() + "/" + b.premiumHard());
        }
        if (u.actionsMinutes() >= b.actionsMinutesHard()) {
            breaches.add("Actions minutes " + u.actionsMinutes() + "/" + b.actionsMinutesHard());
        } else if (u.actionsMinutes() >= b.actionsMinutesSoft()) {
            warnings.add("Actions minutes " + u.actionsMinutes() + "/" + b.actionsMinutesHard());
        }
        Duration age = Duration.between(u.startedAt(), now);
        if (age.compareTo(b.maxRunAge()) >= 0) {
            breaches.add("Run age " + age.toHours() + "h exceeds " + b.maxRunAge().toHours() + "h");
        }

        if (!breaches.isEmpty()) {
            return new BudgetVerdict(BudgetVerdict.Level.BREACH, breaches);
        }
        if (!warnings.isEmpty()) {
            return new BudgetVerdict(BudgetVerdict.Level.WARN, warnings);
        }
        return new BudgetVerdict(BudgetVerdict.Level.OK, List.of());
    }
}
