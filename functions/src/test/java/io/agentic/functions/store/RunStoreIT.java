package io.agentic.functions.store;

import io.agentic.core.budget.Budgets;
import io.agentic.core.budget.RunUsage;
import io.agentic.core.loop.IterationSnapshot;
import io.agentic.core.run.Actor;
import io.agentic.core.run.RunState;
import io.agentic.functions.support.DynamoDbLocal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RunStoreIT {
    private static final Instant NOW = Instant.parse("2026-10-07T12:00:00Z");
    private DynamoDbClient ddb;
    private RunStore store;

    @BeforeEach
    void setUp() {
        ddb = DynamoDbLocal.freshClient();
        store = new RunStore(ddb, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private int ledgerRows(String ticketKey) {
        return ddb.query(QueryRequest.builder().tableName(Tables.LEDGER)
                .keyConditionExpression("ticketKey = :k")
                .expressionAttributeValues(Map.of(":k", AttributeValue.fromS(ticketKey)))
                .build()).count();
    }

    @Test
    void tryStartTwiceOnlyStartsOnce() {
        assertThat(store.tryStart("ABC-1", "r1", "", Budgets.defaults(), NOW)).isTrue();
        assertThat(store.tryStart("ABC-1", "r2", "", Budgets.defaults(), NOW)).isFalse();
        assertThat(store.get("ABC-1")).get().extracting(Run::runId).isEqualTo("r1");
        assertThat(ledgerRows("ABC-1")).isEqualTo(1);
    }

    @Test
    void tryStartAfterDoneStartsAgain() {
        store.tryStart("ABC-2", "r1", "acme/pay", Budgets.defaults(), NOW);
        store.transition("ABC-2", RunState.READINESS, RunState.ABORTED, Actor.HUMAN, "stop");
        assertThat(store.tryStart("ABC-2", "r2", "acme/pay", Budgets.defaults(), NOW)).isTrue();
        assertThat(store.get("ABC-2").orElseThrow().state()).isEqualTo(RunState.READINESS);
    }

    @Test
    void transitionWithWrongFromFailsAndWritesNoLedger() {
        store.tryStart("ABC-3", "r1", "", Budgets.defaults(), NOW);
        assertThatThrownBy(() -> store.transition("ABC-3", RunState.CONTEXT, RunState.CODING, Actor.BOT, "x"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(ledgerRows("ABC-3")).isEqualTo(1);
    }

    @Test
    void moveToCurrentStateWritesNoLedger() {
        store.tryStart("ABC-4", "r1", "", Budgets.defaults(), NOW);
        store.moveTo("ABC-4", RunState.READINESS, Actor.BOT, "same");
        assertThat(ledgerRows("ABC-4")).isEqualTo(1);
        store.moveTo("ABC-4", RunState.CONTEXT, Actor.BOT, "go");
        assertThat(ledgerRows("ABC-4")).isEqualTo(2);
        assertThat(store.get("ABC-4").orElseThrow().state()).isEqualTo(RunState.CONTEXT);
    }

    @Test
    void registerWaitThenDeliverReturnsToken() {
        store.tryStart("ABC-5", "r1", "", Budgets.defaults(), NOW);
        assertThat(store.registerWait("ABC-5", WaitKind.PR_READY, "tok-1")).isEmpty();
        assertThat(store.deliverSignal("ABC-5", WaitKind.PR_READY, "{\"prNumber\":1}")).contains("tok-1");
        assertThat(store.deliverSignal("ABC-5", WaitKind.PR_READY, "{\"prNumber\":2}")).isEmpty();
    }

    @Test
    void signalBeforeWaitIsReturnedAsPending() {
        store.tryStart("ABC-6", "r1", "", Budgets.defaults(), NOW);
        assertThat(store.deliverSignal("ABC-6", WaitKind.CHECKS_COMPLETE, "{\"headSha\":\"abc\"}")).isEmpty();
        assertThat(store.registerWait("ABC-6", WaitKind.CHECKS_COMPLETE, "tok-2")).contains("{\"headSha\":\"abc\"}");
        assertThat(store.registerWait("ABC-6", WaitKind.CHECKS_COMPLETE, "tok-3")).isEmpty();
    }

    @Test
    void markDeliveryIsIdempotent() {
        assertThat(store.markDelivery("d-1", NOW)).isTrue();
        assertThat(store.markDelivery("d-1", NOW)).isFalse();
    }

    @Test
    void roundTripsFieldsAndIndexes() {
        store.tryStart("ABC-7", "r1", "", Budgets.defaults(), NOW);
        store.setRepo("ABC-7", "acme/pay");
        store.setIssue("ABC-7", 101);
        store.setPr("ABC-7", 418);
        store.setSlackThread("ABC-7", "1.2");
        store.saveUsage("ABC-7", new RunUsage(1, 0, 3, 10, NOW));
        store.saveBudgets("ABC-7", Budgets.defaults().raisedBy(1.5));
        store.appendSnapshot("ABC-7", new IterationSnapshot(1, "h", Set.of("a"), Set.of("f"), Set.of("f"), Map.of("f", "x")));
        store.setHumanOverride("ABC-7", true);
        store.setInjectedLessons("ABC-7", List.of("l1", "l2"));
        store.setEscalation("ABC-7", RunState.GATES, Map.of("reason", "stuck", "id", "e1"));

        Run run = store.findByPr("acme/pay", 418).orElseThrow();
        assertThat(run.ticketKey()).isEqualTo("ABC-7");
        assertThat(store.findByIssue("acme/pay", 101)).isPresent();
        assertThat(run.slackThreadTs()).isEqualTo("1.2");
        assertThat(run.usage().premiumRequests()).isEqualTo(3);
        assertThat(run.budgets().premiumHard()).isEqualTo(75);
        assertThat(run.budgets().codingTimeout()).isEqualTo(Budgets.defaults().codingTimeout());
        assertThat(run.snapshots()).hasSize(1);
        assertThat(run.humanOverride()).isTrue();
        assertThat(run.injectedLessonIds()).containsExactly("l1", "l2");
        assertThat(run.escalation()).containsEntry("reason", "stuck");
        assertThat(run.escalatedFrom()).isEqualTo(RunState.GATES);
    }

    @Test
    void lastFindingsRoundTrip() {
        store.tryStart("ABC-8", "r1", "acme/pay", Budgets.defaults(), NOW);
        assertThat(store.lastFindings("ABC-8", io.agentic.functions.gates.GateFindings.class)).isEmpty();
        var findings = new io.agentic.functions.gates.GateFindings("sha",
                List.of(new io.agentic.functions.gates.GateFinding("ci", "ci:build", "a.java", 3, "boom")), List.of(), List.of("x"));
        store.setLastFindings("ABC-8", findings);
        assertThat(store.lastFindings("ABC-8", io.agentic.functions.gates.GateFindings.class)).contains(findings);
    }

    @Test
    void isoWeekFormat() {
        assertThat(RunStore.isoWeek(NOW)).isEqualTo("2026-W41");
    }
}
