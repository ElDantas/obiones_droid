package io.agentic.core.budget;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BudgetsTest {

    @Test
    void defaultsMatchGlobalConstraints() {
        Budgets b = Budgets.defaults();
        assertThat(b.maxGateIterations()).isEqualTo(3);
        assertThat(b.maxHumanIterations()).isEqualTo(3);
        assertThat(b.premiumSoft()).isEqualTo(30);
        assertThat(b.premiumHard()).isEqualTo(50);
        assertThat(b.codingTimeout()).isEqualTo(Duration.ofMinutes(60));
        assertThat(b.maxRunAge()).isEqualTo(Duration.ofDays(3));
        assertThat(b.actionsMinutesSoft()).isEqualTo(120);
        assertThat(b.actionsMinutesHard()).isEqualTo(240);
        assertThat(b.maxDiffLines()).isEqualTo(800);
        assertThat(b.maxDiffFiles()).isEqualTo(25);
        assertThat(b.forbiddenPaths()).contains("infra/**", "**/migrations/**", ".github/workflows/**");
    }

    @Test
    void raisedByScalesIterationsAndUsageButNotDiffLimits() {
        Budgets r = Budgets.defaults().raisedBy(1.5);
        assertThat(r.maxGateIterations()).isEqualTo(5);
        assertThat(r.premiumHard()).isEqualTo(75);
        assertThat(r.actionsMinutesHard()).isEqualTo(360);
        assertThat(r.maxDiffLines()).isEqualTo(800);
        assertThat(r.maxDiffFiles()).isEqualTo(25);
    }

    @Test
    void mergeOverridesOnlyGivenFields() {
        Budgets m = Budgets.defaults().merge(Map.of("maxGateIterations", 5));
        assertThat(m.maxGateIterations()).isEqualTo(5);
        assertThat(m).usingRecursiveComparison().ignoringFields("maxGateIterations").isEqualTo(Budgets.defaults());
    }

    @Test
    void mergeAcceptsLongsAndDurations() {
        Budgets m = Budgets.defaults().merge(Map.of("premiumHard", 70L, "codingTimeoutMinutes", 90, "forbiddenPaths", List.of("x/**")));
        assertThat(m.premiumHard()).isEqualTo(70);
        assertThat(m.codingTimeout()).isEqualTo(Duration.ofMinutes(90));
        assertThat(m.forbiddenPaths()).containsExactly("x/**");
    }
}
