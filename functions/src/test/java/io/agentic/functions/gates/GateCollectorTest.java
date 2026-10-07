package io.agentic.functions.gates;

import io.agentic.core.budget.Budgets;
import io.agentic.functions.ingress.Identities;
import io.agentic.functions.readiness.RepoConfig;
import io.agentic.integrations.github.GitHubClient;
import io.agentic.integrations.github.model.Annotation;
import io.agentic.integrations.github.model.CheckRun;
import io.agentic.integrations.github.model.ReviewComment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GateCollectorTest {
    private GitHubClient github;
    private GateCollector collector;
    private final RepoConfig requiredBuild = new RepoConfig(Budgets.defaults(), List.of(), List.of(), List.of("build"));

    @BeforeEach
    void setUp() {
        github = mock(GitHubClient.class);
        collector = new GateCollector(github, new Identities("agentic-svc", "agentic-bot[bot]"));
        when(github.listReviewComments("acme/payments", 418)).thenReturn(List.of());
    }

    @Test
    void failingRequiredCheckYieldsOneFindingPerAnnotation() {
        when(github.listCheckRuns("acme/payments", "sha")).thenReturn(List.of(new CheckRun(7, "build", "completed", "failure", "2 tests failed", null)));
        when(github.listCheckRunAnnotations("acme/payments", 7)).thenReturn(List.of(
                new Annotation("src/OrderServiceTest.java", 42, "discount_rounding", "expected 10.00 but was 9.99"),
                new Annotation("src/OrderServiceTest.java", 60, "cap", "expected 50 but was 70")));
        GateFindings f = collector.collect("acme/payments", 418, "sha", requiredBuild, 0);
        assertThat(f.blocking()).extracting(GateFinding::id)
                .containsExactly("ci:build:src/OrderServiceTest.java:discount_rounding", "ci:build:src/OrderServiceTest.java:cap");
    }

    @Test
    void failingCheckWithoutAnnotationsUsesSummary() {
        when(github.listCheckRuns("acme/payments", "sha")).thenReturn(List.of(new CheckRun(7, "build", "completed", "timed_out", null, null)));
        when(github.listCheckRunAnnotations("acme/payments", 7)).thenReturn(List.of());
        assertThat(collector.collect("acme/payments", 418, "sha", requiredBuild, 0).blocking())
                .singleElement().satisfies(g -> assertThat(g.message()).isEqualTo("Check timed_out"));
    }

    @Test
    void nonRequiredFailingCheckIsIgnoredWhenRequiredChecksConfigured() {
        when(github.listCheckRuns("acme/payments", "sha")).thenReturn(List.of(new CheckRun(8, "lint", "completed", "failure", "x", null)));
        assertThat(collector.collect("acme/payments", 418, "sha", requiredBuild, 0).blocking()).isEmpty();
    }

    @Test
    void allNonAgenticChecksAreRequiredWhenNoneConfigured() {
        RepoConfig none = new RepoConfig(Budgets.defaults(), List.of(), List.of(), List.of());
        when(github.listCheckRuns("acme/payments", "sha")).thenReturn(List.of(new CheckRun(8, "lint", "completed", "failure", "x", null)));
        when(github.listCheckRunAnnotations("acme/payments", 8)).thenReturn(List.of());
        assertThat(collector.collect("acme/payments", 418, "sha", none, 0).blocking()).hasSize(1);
    }

    @Test
    void failingAgentVerdictYieldsBlockingFindingsPerFinding() {
        String text = "```agentic-verdict\n{\"gate\":\"qa\",\"pass\":false,\"summary\":\"2 gaps\",\"findings\":["
                + "{\"id\":\"QA-1\",\"message\":\"Add test A\"},{\"id\":\"QA-2\",\"message\":\"Add test B\"}]}\n```";
        when(github.listCheckRuns("acme/payments", "sha")).thenReturn(List.of(new CheckRun(9, "agentic/qa", "completed", "failure", "2 gaps", text)));
        assertThat(collector.collect("acme/payments", 418, "sha", requiredBuild, 0).blocking())
                .extracting(GateFinding::id).containsExactly("qa:QA-1", "qa:QA-2");
    }

    @Test
    void missingVerdictIsBlocking() {
        when(github.listCheckRuns("acme/payments", "sha")).thenReturn(List.of(new CheckRun(9, "agentic/ac-review", "completed", "failure", "x", null)));
        assertThat(collector.collect("acme/payments", 418, "sha", requiredBuild, 0).blocking())
                .singleElement().satisfies(f -> assertThat(f.message()).isEqualTo("ac-review: verdict missing or invalid"));
    }

    @Test
    void passingVerdictAddsNothing() {
        String text = "```agentic-verdict\n{\"gate\":\"ac-review\",\"pass\":true,\"summary\":\"ok\",\"findings\":[]}\n```";
        when(github.listCheckRuns("acme/payments", "sha")).thenReturn(List.of(new CheckRun(9, "agentic/ac-review", "completed", "success", "ok", text)));
        assertThat(collector.collect("acme/payments", 418, "sha", requiredBuild, 0).blocking()).isEmpty();
    }

    @Test
    void copilotReviewBlocksOnlyOnFirstPass() {
        when(github.listCheckRuns("acme/payments", "sha")).thenReturn(List.of());
        when(github.listReviewComments("acme/payments", 418)).thenReturn(List.of(
                new ReviewComment(1, "copilot-pull-request-reviewer[bot]", "src/Order.java", 12, "Prefer BigDecimal", "sha", 5L),
                new ReviewComment(2, "copilot-pull-request-reviewer[bot]", "src/Old.java", 3, "stale", "older", 4L),
                new ReviewComment(3, "alice", "src/Order.java", 12, "human", "sha", 6L)));
        GateFindings first = collector.collect("acme/payments", 418, "sha", requiredBuild, 0);
        GateFindings later = collector.collect("acme/payments", 418, "sha", requiredBuild, 1);
        assertThat(first.blocking()).extracting(GateFinding::id).containsExactly("copilot-review:src/Order.java:12");
        assertThat(later.blocking()).isEmpty();
        assertThat(later.advisory()).hasSize(1);
    }
}
