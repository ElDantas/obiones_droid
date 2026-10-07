package io.agentic.functions.store;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import io.agentic.core.budget.Budgets;
import io.agentic.core.budget.RunUsage;
import io.agentic.core.loop.IterationSnapshot;
import io.agentic.core.run.Actor;
import io.agentic.core.run.RunState;
import io.agentic.core.run.Transitions;
import io.agentic.integrations.http.Json;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.ReturnValue;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;
import software.amazon.awssdk.services.dynamodb.model.Update;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemResponse;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class RunStore {
    private static final String START = "START";

    private final DynamoDbClient ddb;
    private final Clock clock;
    private final String runsTable;
    private final String ledgerTable;
    private final String deliveriesTable;

    public RunStore(DynamoDbClient ddb, Clock clock) {
        this(ddb, clock, Tables.RUNS, Tables.LEDGER, Tables.DELIVERIES);
    }

    public RunStore(DynamoDbClient ddb, Clock clock, String runsTable, String ledgerTable, String deliveriesTable) {
        this.ddb = ddb;
        this.clock = clock;
        this.runsTable = runsTable;
        this.ledgerTable = ledgerTable;
        this.deliveriesTable = deliveriesTable;
    }

    public boolean tryStart(String ticketKey, String runId, String repo, Budgets budgets, Instant now) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("ticketKey", s(ticketKey));
        item.put("runId", s(runId));
        item.put("repo", s(repo == null ? "" : repo));
        item.put("state", s(RunState.READINESS.name()));
        item.put("budgetsJson", s(json(budgets)));
        item.put("usageJson", s(json(RunUsage.start(now))));
        item.put("snapshots", AttributeValue.fromL(List.of()));
        item.put("injectedLessonIds", AttributeValue.fromL(List.of()));
        item.put("humanOverride", AttributeValue.fromBool(false));
        item.put("waits", AttributeValue.fromM(Map.of()));
        item.put("pending", AttributeValue.fromM(Map.of()));
        item.put("startedAt", s(now.toString()));

        Put putRun = Put.builder()
                .tableName(runsTable)
                .item(item)
                .conditionExpression("attribute_not_exists(ticketKey) OR #s IN (:done, :aborted, :needsInfo)")
                .expressionAttributeNames(Map.of("#s", "state"))
                .expressionAttributeValues(Map.of(
                        ":done", s(RunState.DONE.name()),
                        ":aborted", s(RunState.ABORTED.name()),
                        ":needsInfo", s(RunState.NEEDS_INFO.name())))
                .build();
        Put putLedger = Put.builder()
                .tableName(ledgerTable)
                .item(ledgerItem(ticketKey, runId, repo, START, RunState.READINESS.name(), now, Actor.BOT, RunUsage.start(now), "Run started"))
                .build();
        try {
            ddb.transactWriteItems(TransactWriteItemsRequest.builder()
                    .transactItems(TransactWriteItem.builder().put(putRun).build(), TransactWriteItem.builder().put(putLedger).build())
                    .build());
            return true;
        } catch (TransactionCanceledException e) {
            return false;
        }
    }

    public Optional<Run> get(String ticketKey) {
        Map<String, AttributeValue> item = ddb.getItem(GetItemRequest.builder()
                .tableName(runsTable)
                .key(key(ticketKey))
                .consistentRead(true)
                .build()).item();
        if (item == null || item.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(toRun(item));
    }

    public Optional<Run> findByIssue(String repo, int issue) {
        return queryIndex("byIssue", "repoIssue", repo + "#" + issue);
    }

    public Optional<Run> findByPr(String repo, int pr) {
        return queryIndex("byPr", "repoPr", repo + "#" + pr);
    }

    public void transition(String ticketKey, RunState from, RunState to, Actor actor, String reason) {
        Transitions.check(from, to);
        Run run = get(ticketKey).orElseThrow(() -> new IllegalStateException("No run for " + ticketKey));
        Instant now = clock.instant();
        Update update = Update.builder()
                .tableName(runsTable)
                .key(key(ticketKey))
                .updateExpression("SET #s = :to")
                .conditionExpression("#s = :from")
                .expressionAttributeNames(Map.of("#s", "state"))
                .expressionAttributeValues(Map.of(":to", s(to.name()), ":from", s(from.name())))
                .build();
        Put ledger = Put.builder()
                .tableName(ledgerTable)
                .item(ledgerItem(ticketKey, run.runId(), run.repo(), from.name(), to.name(), now, actor, run.usage(), reason))
                .build();
        try {
            ddb.transactWriteItems(TransactWriteItemsRequest.builder()
                    .transactItems(TransactWriteItem.builder().update(update).build(), TransactWriteItem.builder().put(ledger).build())
                    .build());
        } catch (TransactionCanceledException e) {
            throw new IllegalStateException("Run " + ticketKey + " is not in state " + from, e);
        }
    }

    public RunState moveTo(String ticketKey, RunState to, Actor actor, String reason) {
        RunState current = get(ticketKey).orElseThrow(() -> new IllegalStateException("No run for " + ticketKey)).state();
        if (current == to) {
            return current;
        }
        transition(ticketKey, current, to, actor, reason);
        return to;
    }

    public void setRepo(String ticketKey, String repo) {
        set(ticketKey, "repo", s(repo));
    }

    public void setExecutionArn(String ticketKey, String arn) {
        set(ticketKey, "executionArn", s(arn));
    }

    public void setIssue(String ticketKey, int issue) {
        Run run = get(ticketKey).orElseThrow();
        update(ticketKey, "SET issueNumber = :n, repoIssue = :ri", Map.of(),
                Map.of(":n", n(issue), ":ri", s(run.repo() + "#" + issue)));
    }

    public void setPr(String ticketKey, int pr) {
        Run run = get(ticketKey).orElseThrow();
        update(ticketKey, "SET prNumber = :n, repoPr = :rp", Map.of(),
                Map.of(":n", n(pr), ":rp", s(run.repo() + "#" + pr)));
    }

    public void setSlackThread(String ticketKey, String ts) {
        set(ticketKey, "slackThreadTs", s(ts));
    }

    public void saveUsage(String ticketKey, RunUsage usage) {
        set(ticketKey, "usageJson", s(json(usage)));
    }

    public RunUsage updateUsage(String ticketKey, java.util.function.UnaryOperator<RunUsage> change) {
        for (int attempt = 0; attempt < 30; attempt++) {
            if (attempt > 0) {
                try {
                    Thread.sleep(java.util.concurrent.ThreadLocalRandom.current().nextLong(5, 25L * Math.min(attempt, 8)));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            Map<String, AttributeValue> item = ddb.getItem(GetItemRequest.builder()
                    .tableName(runsTable).key(key(ticketKey)).consistentRead(true).projectionExpression("usageJson").build()).item();
            String current = item.get("usageJson").s();
            RunUsage next = change.apply(read(current, new TypeReference<RunUsage>() {
            }));
            try {
                ddb.updateItem(UpdateItemRequest.builder()
                        .tableName(runsTable)
                        .key(key(ticketKey))
                        .updateExpression("SET usageJson = :n")
                        .conditionExpression("usageJson = :c")
                        .expressionAttributeValues(Map.of(":n", s(json(next)), ":c", s(current)))
                        .build());
                return next;
            } catch (ConditionalCheckFailedException e) {
                continue;
            }
        }
        throw new IllegalStateException("Could not update usage for " + ticketKey);
    }

    public boolean addToSet(String ticketKey, String attribute, String value) {
        try {
            ddb.updateItem(UpdateItemRequest.builder()
                    .tableName(runsTable)
                    .key(key(ticketKey))
                    .updateExpression("ADD #a :v")
                    .conditionExpression("attribute_exists(ticketKey) AND (attribute_not_exists(#a) OR NOT contains(#a, :s))")
                    .expressionAttributeNames(Map.of("#a", attribute))
                    .expressionAttributeValues(Map.of(":v", AttributeValue.fromSs(List.of(value)), ":s", s(value)))
                    .build());
            return true;
        } catch (ConditionalCheckFailedException e) {
            return false;
        }
    }

    public void setFlag(String ticketKey, String attribute, boolean value) {
        set(ticketKey, attribute, AttributeValue.fromBool(value));
    }

    public boolean flag(String ticketKey, String attribute) {
        Map<String, AttributeValue> item = ddb.getItem(GetItemRequest.builder()
                .tableName(runsTable).key(key(ticketKey)).consistentRead(true).build()).item();
        return item != null && item.containsKey(attribute) && Boolean.TRUE.equals(item.get(attribute).bool());
    }

    public void setString(String ticketKey, String attribute, String value) {
        set(ticketKey, attribute, s(value));
    }

    public Optional<String> string(String ticketKey, String attribute) {
        Map<String, AttributeValue> item = ddb.getItem(GetItemRequest.builder()
                .tableName(runsTable).key(key(ticketKey)).consistentRead(true).build()).item();
        return item != null && item.containsKey(attribute) ? Optional.ofNullable(item.get(attribute).s()) : Optional.empty();
    }

    public void saveBudgets(String ticketKey, Budgets budgets) {
        set(ticketKey, "budgetsJson", s(json(budgets)));
    }

    public void appendSnapshot(String ticketKey, IterationSnapshot snapshot) {
        update(ticketKey, "SET snapshots = list_append(if_not_exists(snapshots, :empty), :s)", Map.of(),
                Map.of(":empty", AttributeValue.fromL(List.of()), ":s", AttributeValue.fromL(List.of(s(json(snapshot))))));
    }

    public void setHumanOverride(String ticketKey, boolean value) {
        set(ticketKey, "humanOverride", AttributeValue.fromBool(value));
    }

    public void setInjectedLessons(String ticketKey, List<String> ids) {
        set(ticketKey, "injectedLessonIds", AttributeValue.fromL(ids.stream().map(RunStore::s).toList()));
    }

    public void setEscalation(String ticketKey, RunState from, Map<String, Object> escalation) {
        Object id = escalation.get("id");
        update(ticketKey, "SET escalatedFrom = :f, escalationJson = :e, escalationId = :i REMOVE escalationResolvedBy", Map.of(),
                Map.of(":f", s(from.name()), ":e", s(json(escalation)), ":i", s(id == null ? "" : id.toString())));
    }

    public void updateEscalation(String ticketKey, Map<String, Object> escalation) {
        set(ticketKey, "escalationJson", s(json(escalation)));
    }

    public boolean claimEscalation(String ticketKey, String escalationId, String userId) {
        try {
            ddb.updateItem(UpdateItemRequest.builder()
                    .tableName(runsTable)
                    .key(key(ticketKey))
                    .updateExpression("SET escalationResolvedBy = :u")
                    .conditionExpression("escalationId = :i AND attribute_not_exists(escalationResolvedBy) AND #s = :esc")
                    .expressionAttributeNames(Map.of("#s", "state"))
                    .expressionAttributeValues(Map.of(":u", s(userId), ":i", s(escalationId), ":esc", s(RunState.ESCALATED.name())))
                    .build());
            return true;
        } catch (ConditionalCheckFailedException e) {
            return false;
        }
    }

    public void setLastFindings(String ticketKey, Object findings) {
        set(ticketKey, "lastFindingsJson", s(json(findings)));
    }

    public <T> Optional<T> lastFindings(String ticketKey, Class<T> type) {
        Map<String, AttributeValue> item = ddb.getItem(GetItemRequest.builder()
                .tableName(runsTable)
                .key(key(ticketKey))
                .consistentRead(true)
                .projectionExpression("lastFindingsJson")
                .build()).item();
        if (item == null || !item.containsKey("lastFindingsJson")) {
            return Optional.empty();
        }
        try {
            return Optional.of(Json.MAPPER.readValue(item.get("lastFindingsJson").s(), type));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    public Optional<String> registerWait(String ticketKey, WaitKind kind, String taskToken) {
        Optional<String> pending = take(ticketKey, "pending", kind);
        if (pending.isPresent()) {
            return pending;
        }
        put(ticketKey, "waits", kind, taskToken);
        Optional<String> late = take(ticketKey, "pending", kind);
        if (late.isPresent()) {
            take(ticketKey, "waits", kind);
            return late;
        }
        return Optional.empty();
    }

    public Optional<String> deliverSignal(String ticketKey, WaitKind kind, String payloadJson) {
        Optional<String> token = take(ticketKey, "waits", kind);
        if (token.isPresent()) {
            return token;
        }
        put(ticketKey, "pending", kind, payloadJson);
        Optional<String> late = take(ticketKey, "waits", kind);
        if (late.isPresent()) {
            take(ticketKey, "pending", kind);
            return late;
        }
        return Optional.empty();
    }

    public boolean markDelivery(String deliveryId, Instant now) {
        try {
            ddb.putItem(PutItemRequest.builder()
                    .tableName(deliveriesTable)
                    .item(Map.of(
                            "deliveryId", s(deliveryId),
                            "receivedAt", s(now.toString()),
                            "expiresAt", n(now.plus(Duration.ofDays(7)).getEpochSecond())))
                    .conditionExpression("attribute_not_exists(deliveryId)")
                    .build());
            return true;
        } catch (ConditionalCheckFailedException e) {
            return false;
        }
    }

    private Optional<String> take(String ticketKey, String map, WaitKind kind) {
        try {
            UpdateItemResponse res = ddb.updateItem(UpdateItemRequest.builder()
                    .tableName(runsTable)
                    .key(key(ticketKey))
                    .updateExpression("REMOVE #m.#k")
                    .conditionExpression("attribute_exists(#m.#k)")
                    .expressionAttributeNames(Map.of("#m", map, "#k", kind.name()))
                    .returnValues(ReturnValue.UPDATED_OLD)
                    .build());
            AttributeValue m = res.attributes().get(map);
            if (m == null || m.m() == null || !m.m().containsKey(kind.name())) {
                return Optional.empty();
            }
            return Optional.of(m.m().get(kind.name()).s());
        } catch (ConditionalCheckFailedException e) {
            return Optional.empty();
        }
    }

    private void put(String ticketKey, String map, WaitKind kind, String value) {
        update(ticketKey, "SET #m.#k = :v", Map.of("#m", map, "#k", kind.name()), Map.of(":v", s(value)));
    }

    private void set(String ticketKey, String attribute, AttributeValue value) {
        update(ticketKey, "SET #a = :v", Map.of("#a", attribute), Map.of(":v", value));
    }

    private void update(String ticketKey, String expression, Map<String, String> names, Map<String, AttributeValue> values) {
        UpdateItemRequest.Builder b = UpdateItemRequest.builder()
                .tableName(runsTable)
                .key(key(ticketKey))
                .updateExpression(expression)
                .conditionExpression("attribute_exists(ticketKey)")
                .expressionAttributeValues(values);
        if (!names.isEmpty()) {
            b.expressionAttributeNames(names);
        }
        ddb.updateItem(b.build());
    }

    private Optional<Run> queryIndex(String index, String attribute, String value) {
        List<Map<String, AttributeValue>> items = ddb.query(QueryRequest.builder()
                .tableName(runsTable)
                .indexName(index)
                .keyConditionExpression("#a = :v")
                .expressionAttributeNames(Map.of("#a", attribute))
                .expressionAttributeValues(Map.of(":v", s(value)))
                .build()).items();
        if (items.isEmpty()) {
            return Optional.empty();
        }
        return get(items.get(0).get("ticketKey").s());
    }

    private Map<String, AttributeValue> ledgerItem(String ticketKey, String runId, String repo, String from, String to,
                                                   Instant ts, Actor actor, RunUsage usage, String reason) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("ticketKey", s(ticketKey));
        item.put("tsSeq", s(ts + "#" + runId + "#" + UUID.randomUUID().toString().substring(0, 8)));
        item.put("ts", s(ts.toString()));
        item.put("week", s(isoWeek(ts)));
        item.put("runId", s(runId));
        item.put("repo", s(repo == null ? "" : repo));
        item.put("from", s(from));
        item.put("to", s(to));
        item.put("actor", s(actor.name()));
        item.put("iteration", n(usage.gateIterations() + usage.humanIterations()));
        item.put("premiumRequests", n(usage.premiumRequests()));
        item.put("actionsMinutes", n(usage.actionsMinutes()));
        item.put("reason", s(reason == null ? "" : reason));
        return item;
    }

    static String isoWeek(Instant ts) {
        var d = ts.atZone(ZoneOffset.UTC);
        return String.format("%d-W%02d", d.get(IsoFields.WEEK_BASED_YEAR), d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
    }

    private Run toRun(Map<String, AttributeValue> i) {
        List<IterationSnapshot> snapshots = new ArrayList<>();
        if (i.containsKey("snapshots")) {
            for (AttributeValue v : i.get("snapshots").l()) {
                snapshots.add(read(v.s(), new TypeReference<>() {
                }));
            }
        }
        List<String> lessons = i.containsKey("injectedLessonIds")
                ? i.get("injectedLessonIds").l().stream().map(AttributeValue::s).toList()
                : List.of();
        Map<String, Object> escalation = i.containsKey("escalationJson")
                ? read(i.get("escalationJson").s(), new TypeReference<>() {
        })
                : Map.of();
        return new Run(
                i.get("ticketKey").s(),
                str(i, "runId"),
                str(i, "repo"),
                RunState.valueOf(i.get("state").s()),
                num(i, "issueNumber"),
                num(i, "prNumber"),
                str(i, "executionArn"),
                str(i, "slackThreadTs"),
                read(i.get("budgetsJson").s(), new TypeReference<>() {
                }),
                read(i.get("usageJson").s(), new TypeReference<>() {
                }),
                snapshots,
                i.containsKey("humanOverride") && Boolean.TRUE.equals(i.get("humanOverride").bool()),
                lessons,
                escalation,
                i.containsKey("escalatedFrom") ? RunState.valueOf(i.get("escalatedFrom").s()) : null);
    }

    private static String str(Map<String, AttributeValue> i, String k) {
        return i.containsKey(k) ? i.get(k).s() : null;
    }

    private static Integer num(Map<String, AttributeValue> i, String k) {
        return i.containsKey(k) && i.get(k).n() != null ? Integer.valueOf(i.get(k).n()) : null;
    }

    private static Map<String, AttributeValue> key(String ticketKey) {
        return Map.of("ticketKey", s(ticketKey));
    }

    private static AttributeValue s(String v) {
        return AttributeValue.fromS(v);
    }

    private static AttributeValue n(long v) {
        return AttributeValue.fromN(Long.toString(v));
    }

    private static String json(Object o) {
        try {
            return Json.MAPPER.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static <T> T read(String text, TypeReference<T> type) {
        try {
            return Json.MAPPER.readValue(text, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
