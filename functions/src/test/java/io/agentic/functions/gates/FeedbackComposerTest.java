package io.agentic.functions.gates;

import io.agentic.core.budget.Budgets;
import io.agentic.functions.readiness.RepoConfig;
import io.agentic.integrations.github.model.ReviewComment;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FeedbackComposerTest {
    private final FeedbackComposer composer = new FeedbackComposer();
    private final RepoConfig config = new RepoConfig(Budgets.defaults(), List.of(), List.of(), List.of());

    @Test
    void groupsByGateAndMentionsCopilot() {
        String text = composer.forGates(new GateFindings("abc1234def", List.of(
                new GateFinding("ci", "ci:build:src/A.java:t", "src/A.java", 42, "expected 10.00 but was 9.99"),
                new GateFinding("copilot-review", "copilot-review:src/B.java:12", "src/B.java", 12, "Prefer BigDecimal")), List.of(), List.of()), config);
        assertThat(text).startsWith("@copilot The automated gates failed on commit abc1234.")
                .contains("### CI\n- `build` — src/A.java:42 — expected 10.00 but was 9.99")
                .contains("### Copilot review\n- src/B.java:12 — Prefer BigDecimal")
                .contains("Do not modify files matching: infra/**");
    }

    @Test
    void capsAtThirtyFindings() {
        List<GateFinding> many = new ArrayList<>();
        for (int i = 0; i < 35; i++) {
            many.add(new GateFinding("ci", "ci:build:f" + i, "f" + i, i, "m"));
        }
        String text = composer.forGates(new GateFindings("abc", many, List.of(), List.of()), config);
        assertThat(text).contains("…and 5 more");
        assertThat(text.lines().filter(l -> l.startsWith("- ")).count()).isEqualTo(30);
    }

    @Test
    void humanReviewIncludesBodyAndInlineComments() {
        String text = composer.forHumanReview(List.of(new ReviewComment(1, "alice", "src/A.java", 3, "Use the repo layer", "s", 9L)), "Please refactor");
        assertThat(text).startsWith("@copilot").contains("Please refactor").contains("`src/A.java:3` — Use the repo layer");
    }
}
