package io.agentic.core.budget;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static io.agentic.core.budget.BudgetEvaluator.IterationKind.GATE;
import static io.agentic.core.budget.BudgetEvaluator.IterationKind.HUMAN;
import static io.agentic.core.budget.BudgetEvaluator.IterationKind.NONE;
import static org.assertj.core.api.Assertions.assertThat;

class BudgetEvaluatorTest {
    private static final Instant NOW = Instant.parse("2026-10-07T12:00:00Z");
    private final BudgetEvaluator evaluator = new BudgetEvaluator();
    private final Budgets budgets = Budgets.defaults();

    private RunUsage usage(int gate, int human, int premium, int minutes) {
        return new RunUsage(gate, human, premium, minutes, NOW.minus(Duration.ofHours(1)));
    }

    @Test
    void gateIterationLimitBreachesOnlyForGateKind() {
        assertThat(evaluator.evaluate(budgets, usage(3, 0, 0, 0), NOW, GATE).level()).isEqualTo(BudgetVerdict.Level.BREACH);
        assertThat(evaluator.evaluate(budgets, usage(3, 0, 0, 0), NOW, NONE).level()).isEqualTo(BudgetVerdict.Level.OK);
    }

    @Test
    void humanIterationLimitBreaches() {
        assertThat(evaluator.evaluate(budgets, usage(0, 3, 0, 0), NOW, HUMAN).isBreach()).isTrue();
    }

    @Test
    void premiumSoftWarnsAndHardBreaches() {
        assertThat(evaluator.evaluate(budgets, usage(0, 0, 30, 0), NOW, NONE).level()).isEqualTo(BudgetVerdict.Level.WARN);
        assertThat(evaluator.evaluate(budgets, usage(0, 0, 50, 0), NOW, NONE).level()).isEqualTo(BudgetVerdict.Level.BREACH);
    }

    @Test
    void actionsMinutesSoftWarnsAndHardBreaches() {
        assertThat(evaluator.evaluate(budgets, usage(0, 0, 0, 120), NOW, NONE).level()).isEqualTo(BudgetVerdict.Level.WARN);
        assertThat(evaluator.evaluate(budgets, usage(0, 0, 0, 240), NOW, NONE).level()).isEqualTo(BudgetVerdict.Level.BREACH);
    }

    @Test
    void runAgeOfThreeDaysBreaches() {
        RunUsage old = new RunUsage(0, 0, 0, 0, NOW.minus(Duration.ofHours(72)));
        BudgetVerdict v = evaluator.evaluate(budgets, old, NOW, NONE);
        assertThat(v.isBreach()).isTrue();
        assertThat(v.reasons()).anyMatch(r -> r.startsWith("Run age 72h"));
    }

    @Test
    void justBelowLimitsIsOk() {
        BudgetVerdict v = evaluator.evaluate(budgets, usage(2, 2, 29, 119), NOW, GATE);
        assertThat(v.level()).isEqualTo(BudgetVerdict.Level.OK);
        assertThat(v.reasons()).isEmpty();
    }
}
