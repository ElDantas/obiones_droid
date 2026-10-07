package io.agentic.local.e2e;

import io.agentic.core.run.RunState;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static io.agentic.local.e2e.E2eDriver.fixture;
import static org.assertj.core.api.Assertions.assertThat;

class FailurePathsE2E {
    private static final Duration T = Duration.ofSeconds(180);
    private static final String FAILING_CHECKS = """
            {"priority":1,"request":{"method":"GET","urlPath":"/repos/acme/payments/commits/abc123/check-runs"},
             "response":{"status":200,"headers":{"Content-Type":"application/json"},"jsonBody":{"total_count":1,"check_runs":[
               {"id":7,"name":"build","status":"completed","conclusion":"failure","output":{"summary":"1 test failed"}}]}}}""";
    private static final String NO_ANNOTATIONS = """
            {"priority":1,"request":{"method":"GET","urlPath":"/repos/acme/payments/check-runs/7/annotations"},
             "response":{"status":200,"headers":{"Content-Type":"application/json"},"jsonBody":[
               {"path":"src/test/java/OrderTest.java","start_line":12,"title":"cap","message":"expected 50 but was 70"}]}}""";
    private static E2eDriver driver;

    @BeforeAll
    static void start() throws Exception {
        driver = new E2eDriver();
    }

    @AfterAll
    static void stop() {
        driver.close();
    }

    @BeforeEach
    void reset() {
        driver.reset();
    }

    @Test
    void vagueTicketNeedsInfo() {
        driver.jira("VAGUE-1", "approved");
        driver.awaitState("VAGUE-1", RunState.NEEDS_INFO, T);
        driver.awaitExecutionStatus("VAGUE-1", "SUCCEEDED", T);
        assertThat(driver.wiremockCount("POST", "/rest/api/3/issue/VAGUE-1/comment", "Clarify:")).isEqualTo(1);
        assertThat(driver.wiremockCount("POST", "/repos/acme/payments/issues", null)).isZero();
    }

    @Test
    void doubleFireStartsOneExecution() {
        java.time.Instant started = java.time.Instant.now().minusSeconds(1);
        assertThat(driver.jira("E2E-3", "approved").body()).contains("STARTED");
        assertThat(driver.jira("E2E-3", "approved").body()).contains("ALREADY_RUNNING");
        driver.awaitState("E2E-3", RunState.CODING, T);
        assertThat(driver.executionsFor("E2E-3", started)).isEqualTo(1);
        assertThat(driver.wiremockCount("POST", "/repos/acme/payments/issues", null)).isEqualTo(1);
    }

    @Test
    void failingChecksSendConsolidatedFeedbackAsServiceUser() {
        driver.stub(FAILING_CHECKS);
        driver.stub(NO_ANNOTATIONS);
        driver.jira("E2E-4", "approved");
        driver.awaitState("E2E-4", RunState.CODING, T);
        driver.github("pull_request", fixture("pr-opened.json", Map.of("pr", "418")));
        driver.github("pull_request", fixture("review-requested.json", Map.of()));
        driver.awaitWait("E2E-4", "CHECKS_COMPLETE", T);
        driver.github("check_suite", fixture("check-suite-completed.json", Map.of("conclusion", "failure")));
        driver.awaitState("E2E-4", RunState.FIXING, T);
        driver.awaitWait("E2E-4", "PR_UPDATED", T);
        assertThat(driver.wiremockCount("POST", "/repos/acme/payments/issues/418/comments", "@copilot The automated gates failed")).isEqualTo(1);
    }

    @Test
    void secondCopilotPrEscalatesAsAmbiguous() {
        driver.jira("E2E-5", "approved");
        driver.awaitState("E2E-5", RunState.CODING, T);
        driver.awaitWait("E2E-5", "PR_READY", T);
        driver.github("pull_request", fixture("pr-opened.json", Map.of("pr", "418")));
        driver.github("pull_request", fixture("pr-opened.json", Map.of("pr", "419")));
        driver.awaitState("E2E-5", RunState.ESCALATED, T);
    }

    @Test
    void escalationResolvedFromSlackAbortsOnce() {
        driver.jira("E2E-8", "approved");
        driver.awaitState("E2E-8", RunState.CODING, T);
        driver.awaitWait("E2E-8", "PR_READY", T);
        driver.github("pull_request", fixture("pr-opened.json", Map.of("pr", "418")));
        driver.github("pull_request", fixture("pr-opened.json", Map.of("pr", "419")));
        driver.awaitState("E2E-8", RunState.ESCALATED, T);
        driver.awaitWait("E2E-8", "ESCALATION_DECISION", T);
        String id = driver.escalationId("E2E-8");
        assertThat(driver.slackAction("U123", "E2E-8", id, "ABORT").statusCode()).isEqualTo(200);
        driver.slackAction("U123", "E2E-8", id, "ABORT");
        driver.awaitState("E2E-8", RunState.ABORTED, T);
        driver.awaitExecutionStatus("E2E-8", "SUCCEEDED", T);
        assertThat(driver.wiremockCount("POST", "/api/chat.postEphemeral", "Already handled")).isEqualTo(1);
    }

    @Test
    void unflagAbortsTheRun() {
        driver.jira("E2E-6", "approved");
        driver.awaitState("E2E-6", RunState.CODING, T);
        driver.github("pull_request", fixture("pr-opened.json", Map.of("pr", "418")));
        assertThat(driver.jira("E2E-6", "unflagged").body()).contains("ABORTED");
        driver.awaitState("E2E-6", RunState.ABORTED, T);
        assertThat(driver.wiremockCount("PATCH", "/repos/acme/payments/pulls/418", null)).isEqualTo(1);
    }

    @Test
    void sameFailureTwiceEscalates() {
        driver.stub(FAILING_CHECKS);
        driver.stub(NO_ANNOTATIONS);
        driver.jira("E2E-7", "approved");
        driver.awaitState("E2E-7", RunState.CODING, T);
        driver.github("pull_request", fixture("pr-opened.json", Map.of("pr", "418")));
        driver.github("pull_request", fixture("review-requested.json", Map.of()));
        driver.awaitWait("E2E-7", "CHECKS_COMPLETE", T);
        driver.github("check_suite", fixture("check-suite-completed.json", Map.of("conclusion", "failure")));
        driver.awaitWait("E2E-7", "PR_UPDATED", T);
        driver.github("pull_request", fixture("review-requested.json", Map.of()));
        driver.awaitWait("E2E-7", "CHECKS_COMPLETE", T);
        driver.github("check_suite", fixture("check-suite-completed.json", Map.of("conclusion", "failure")));
        driver.awaitState("E2E-7", RunState.ESCALATED, T);
        assertThat(driver.wiremockCount("POST", "/repos/acme/payments/issues/418/comments", "@copilot The automated gates failed")).isEqualTo(1);
        assertThat(driver.wiremockCount("POST", "/api/chat.postMessage", "Same failure repeated")).isGreaterThanOrEqualTo(1);
    }
}
