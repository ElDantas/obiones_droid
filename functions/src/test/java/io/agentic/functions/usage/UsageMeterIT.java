package io.agentic.functions.usage;

import io.agentic.core.budget.Budgets;
import io.agentic.functions.store.RunStore;
import io.agentic.functions.support.DynamoDbLocal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class UsageMeterIT {
    private RunStore store;
    private UsageMeter meter;

    @BeforeEach
    void setUp() {
        Instant now = Instant.parse("2026-10-07T12:00:00Z");
        store = new RunStore(DynamoDbLocal.freshClient(), Clock.fixed(now, ZoneOffset.UTC));
        store.tryStart("ABC-1", "r1", "acme/payments", Budgets.defaults(), now);
        meter = new UsageMeter(store);
    }

    @Test
    void sameWorkflowRunCountedOnce() {
        meter.recordActionsMinutes("ABC-1", "555", 3);
        meter.recordActionsMinutes("ABC-1", "555", 3);
        assertThat(store.get("ABC-1").orElseThrow().usage().actionsMinutes()).isEqualTo(3);
    }

    @Test
    void sameVerdictCheckRunCountedOnce() {
        meter.recordGateAgentRequests("ABC-1", "77", 1);
        meter.recordGateAgentRequests("ABC-1", "77", 1);
        meter.recordGateAgentRequests("ABC-1", "78", 1);
        assertThat(store.get("ABC-1").orElseThrow().usage().premiumRequests()).isEqualTo(2);
    }

    @Test
    void concurrentIncrementsAreNotLost() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        for (int i = 0; i < 20; i++) {
            pool.submit(() -> meter.recordCopilotSession("ABC-1"));
        }
        pool.shutdown();
        assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();
        assertThat(store.get("ABC-1").orElseThrow().usage().premiumRequests()).isEqualTo(20);
    }

    @Test
    void warnOnceOnlyOnce() {
        assertThat(meter.warnOnce("ABC-1", "budget-warn", "x")).isTrue();
        assertThat(meter.warnOnce("ABC-1", "budget-warn", "x")).isFalse();
    }
}
