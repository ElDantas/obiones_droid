package io.agentic.functions.ingress;

import io.agentic.core.run.Actor;
import io.agentic.core.run.RunState;
import io.agentic.functions.run.AbortService;
import io.agentic.functions.store.RunStore;
import io.agentic.functions.store.WaitKind;
import io.agentic.integrations.github.GitHubClient;
import io.agentic.integrations.github.model.CheckRun;
import io.agentic.integrations.github.model.PullRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static io.agentic.functions.support.Fixtures.json;
import static io.agentic.functions.support.Fixtures.run;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GitHubEventRouterTest {
    private RunStore store;
    private GitHubClient github;
    private AbortService abort;
    private GitHubEventRouter router;

    @BeforeEach
    void setUp() {
        store = mock(RunStore.class);
        github = mock(GitHubClient.class);
        abort = mock(AbortService.class);
        router = new GitHubEventRouter(store, github, new Identities("agentic-svc", "agentic-bot[bot]"), abort);
    }

    private static PullRequest pr(String sha) {
        return new PullRequest(418, "open", true, false, sha, "copilot/fix-101", "Copilot", "u", 1, 1, 1, "PR_1");
    }

    @Test
    void copilotPrOpenedLinksPrToRun() {
        when(github.closingIssues("acme/payments", 418)).thenReturn(List.of(101));
        when(store.findByIssue("acme/payments", 101)).thenReturn(Optional.of(run("ABC-1", RunState.CODING, 101, null)));
        assertThat(router.route("pull_request", json("github/pull_request.opened.json"))).isEmpty();
        verify(store).setPr("ABC-1", 418);
    }

    @Test
    void secondCopilotPrForSameIssueIsAmbiguous() {
        when(github.closingIssues("acme/payments", 418)).thenReturn(List.of(101));
        when(store.findByIssue("acme/payments", 101)).thenReturn(Optional.of(run("ABC-1", RunState.CODING, 101, 417)));
        List<RoutedSignal> s = router.route("pull_request", json("github/pull_request.opened.json"));
        assertThat(s).singleElement().satisfies(sig -> {
            assertThat(sig.kind()).isEqualTo(WaitKind.PR_READY);
            assertThat(sig.payloadJson()).contains("\"ambiguous\":true");
        });
        verify(store, never()).setPr("ABC-1", 418);
    }

    @Test
    void reviewRequestedWhileCodingIsPrReady() {
        when(store.findByPr("acme/payments", 418)).thenReturn(Optional.of(run("ABC-1", RunState.CODING, 101, 418)));
        assertThat(router.route("pull_request", json("github/pull_request.review_requested.json")))
                .singleElement().extracting(RoutedSignal::kind).isEqualTo(WaitKind.PR_READY);
    }

    @Test
    void reviewRequestedWhileFixingIsPrUpdated() {
        when(store.findByPr("acme/payments", 418)).thenReturn(Optional.of(run("ABC-1", RunState.FIXING, 101, 418)));
        assertThat(router.route("pull_request", json("github/pull_request.review_requested.json")))
                .singleElement().extracting(RoutedSignal::kind).isEqualTo(WaitKind.PR_UPDATED);
    }

    @Test
    void reviewRequestedWhileEscalatedFromCodingIsPrReady() {
        when(store.findByPr("acme/payments", 418)).thenReturn(Optional.of(run("ABC-1", RunState.ESCALATED, 101, 418, RunState.CODING)));
        assertThat(router.route("pull_request", json("github/pull_request.review_requested.json")))
                .singleElement().extracting(RoutedSignal::kind).isEqualTo(WaitKind.PR_READY);
    }

    @Test
    void checksCompleteOnHeadShaSignals() {
        when(store.findByPr("acme/payments", 418)).thenReturn(Optional.of(run("ABC-1", RunState.CODING, 101, 418)));
        when(github.getPullRequest("acme/payments", 418)).thenReturn(pr("abc123"));
        when(github.listCheckRuns("acme/payments", "abc123")).thenReturn(List.of(new CheckRun(1, "build", "completed", "success", null, null)));
        assertThat(router.route("check_suite", json("github/check_suite.completed.json")))
                .singleElement().extracting(RoutedSignal::kind).isEqualTo(WaitKind.CHECKS_COMPLETE);
    }

    @Test
    void checksStillRunningDoNotSignal() {
        when(store.findByPr("acme/payments", 418)).thenReturn(Optional.of(run("ABC-1", RunState.CODING, 101, 418)));
        when(github.getPullRequest("acme/payments", 418)).thenReturn(pr("abc123"));
        when(github.listCheckRuns("acme/payments", "abc123")).thenReturn(List.of(
                new CheckRun(1, "build", "completed", "success", null, null),
                new CheckRun(2, "lint", "in_progress", null, null, null)));
        assertThat(router.route("check_suite", json("github/check_suite.completed.json"))).isEmpty();
    }

    @Test
    void checksForStaleShaDoNotSignal() {
        when(store.findByPr("acme/payments", 418)).thenReturn(Optional.of(run("ABC-1", RunState.CODING, 101, 418)));
        when(github.getPullRequest("acme/payments", 418)).thenReturn(pr("newer"));
        assertThat(router.route("check_suite", json("github/check_suite.completed.json"))).isEmpty();
    }

    @Test
    void humanChangesRequestedDuringReviewSignalsOutcome() {
        when(store.findByPr("acme/payments", 418)).thenReturn(Optional.of(run("ABC-1", RunState.HUMAN_REVIEW, 101, 418)));
        assertThat(router.route("pull_request_review", json("github/pull_request_review.submitted.json")))
                .singleElement().satisfies(s -> {
                    assertThat(s.kind()).isEqualTo(WaitKind.HUMAN_OUTCOME);
                    assertThat(s.payloadJson()).contains("CHANGES_REQUESTED").contains("9001");
                });
    }

    @Test
    void copilotReviewerDoesNotSignal() {
        when(store.findByPr("acme/payments", 418)).thenReturn(Optional.of(run("ABC-1", RunState.HUMAN_REVIEW, 101, 418)));
        assertThat(router.route("pull_request_review", json("github/pull_request_review.copilot.json"))).isEmpty();
    }

    @Test
    void mergedDuringHumanReviewSignalsMerged() {
        when(store.findByPr("acme/payments", 418)).thenReturn(Optional.of(run("ABC-1", RunState.HUMAN_REVIEW, 101, 418)));
        assertThat(router.route("pull_request", json("github/pull_request.closed.merged.json")))
                .singleElement().satisfies(s -> assertThat(s.payloadJson()).contains("MERGED"));
    }

    @Test
    void closedWhileCodingAborts() {
        when(store.findByPr("acme/payments", 418)).thenReturn(Optional.of(run("ABC-1", RunState.CODING, 101, 418)));
        assertThat(router.route("pull_request", json("github/pull_request.closed.merged.json"))).isEmpty();
        verify(abort).abort(eq("ABC-1"), eq(Actor.HUMAN), anyString());
    }

    @Test
    void humanPushSetsOverride() {
        when(github.findOpenPullRequestsByHead("acme/payments", "copilot/fix-101")).thenReturn(List.of(418));
        when(store.findByPr("acme/payments", 418)).thenReturn(Optional.of(run("ABC-1", RunState.FIXING, 101, 418)));
        router.route("push", json("github/push.human.json"));
        verify(store).setHumanOverride("ABC-1", true);
    }
}
