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

class HappyPathE2E {
    private static final Duration T = Duration.ofSeconds(180);
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
    void flaggedTicketBecomesMergedPr() {
        assertThat(driver.jira("E2E-1", "approved").body()).contains("STARTED");
        driver.awaitState("E2E-1", RunState.CODING, T);
        driver.awaitWait("E2E-1", "PR_READY", T);

        driver.github("pull_request", fixture("pr-opened.json", Map.of("pr", "418")));
        driver.github("pull_request", fixture("review-requested.json", Map.of()));
        driver.awaitWait("E2E-1", "CHECKS_COMPLETE", T);
        driver.github("check_suite", fixture("check-suite-completed.json", Map.of("conclusion", "success")));

        driver.awaitState("E2E-1", RunState.HUMAN_REVIEW, T);
        driver.awaitWait("E2E-1", "HUMAN_OUTCOME", T);
        driver.github("pull_request", fixture("pr-closed.json", Map.of("merged", "true")));

        driver.awaitState("E2E-1", RunState.DONE, T);
        driver.awaitExecutionStatus("E2E-1", "SUCCEEDED", T);
        assertThat(driver.wiremockCount("POST", "/repos/acme/payments/issues", null)).isEqualTo(1);
        assertThat(driver.wiremockCount("POST", "/graphql", "replaceActorsForAssignable")).isEqualTo(1);
        assertThat(driver.wiremockCount("POST", "/graphql", "markPullRequestReadyForReview")).isEqualTo(1);
        assertThat(driver.wiremockCount("POST", "/rest/api/3/issue/E2E-1/transitions", "\\\"21\\\"")).isEqualTo(1);
        assertThat(driver.wiremockCount("POST", "/rest/api/3/issue/E2E-1/transitions", "\\\"31\\\"")).isEqualTo(1);
        assertThat(driver.wiremockCount("POST", "/api/chat.postMessage", null)).isGreaterThanOrEqualTo(5);
    }

    @Test
    void checksFinishingBeforeTheWaitAreNotLost() {
        driver.jira("E2E-2", "approved");
        driver.awaitState("E2E-2", RunState.CODING, T);
        driver.github("pull_request", fixture("pr-opened.json", Map.of("pr", "418")));
        driver.github("check_suite", fixture("check-suite-completed.json", Map.of("conclusion", "success")));
        driver.github("pull_request", fixture("review-requested.json", Map.of()));
        driver.awaitState("E2E-2", RunState.HUMAN_REVIEW, T);
    }
}
