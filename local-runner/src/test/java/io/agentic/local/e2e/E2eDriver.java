package io.agentic.local.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import io.agentic.core.run.RunState;
import io.agentic.functions.store.Run;
import io.agentic.functions.store.RunStore;
import io.agentic.functions.store.Tables;
import io.agentic.integrations.http.Json;
import io.agentic.local.LocalApiServer;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.sfn.SfnClient;
import software.amazon.awssdk.services.sfn.model.DescribeExecutionRequest;
import software.amazon.awssdk.services.sfn.model.ExecutionListItem;
import software.amazon.awssdk.services.sfn.model.ListExecutionsRequest;
import software.amazon.awssdk.services.sfn.model.ListStateMachinesRequest;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

final class E2eDriver implements AutoCloseable {
    static final String WIREMOCK = "http://localhost:8089";
    private static final String JIRA_TOKEN = "local-jira-token";
    private static final String WEBHOOK_SECRET = "local-webhook-secret";

    private final LocalApiServer server;
    private final HttpClient http = HttpClient.newHttpClient();
    private final DynamoDbClient ddb;
    private final SfnClient sfn;
    private final RunStore store;

    E2eDriver() throws IOException {
        LocalApiServer.configureLocalAws();
        server = new LocalApiServer(0);
        server.start();
        ddb = DynamoDbClient.create();
        sfn = SfnClient.create();
        store = new RunStore(ddb, Clock.systemUTC());
    }

    void reset() {
        for (Map<String, AttributeValue> item : ddb.scan(ScanRequest.builder().tableName(Tables.RUNS).build()).items()) {
            ddb.deleteItem(DeleteItemRequest.builder().tableName(Tables.RUNS).key(Map.of("ticketKey", item.get("ticketKey"))).build());
        }
        for (Map<String, AttributeValue> item : ddb.scan(ScanRequest.builder().tableName(Tables.DELIVERIES).build()).items()) {
            ddb.deleteItem(DeleteItemRequest.builder().tableName(Tables.DELIVERIES).key(Map.of("deliveryId", item.get("deliveryId"))).build());
        }
        send("POST", WIREMOCK + "/__admin/mappings/reset", "", Map.of());
        send("DELETE", WIREMOCK + "/__admin/requests", null, Map.of());
    }

    HttpResponse<String> jira(String ticketKey, String event) {
        return send("POST", api("/jira/events"), "{\"ticketKey\":\"" + ticketKey + "\",\"event\":\"" + event + "\"}",
                Map.of("x-agentic-token", JIRA_TOKEN));
    }

    HttpResponse<String> github(String event, String body) {
        return send("POST", api("/github/webhook"), body, Map.of(
                "x-github-event", event,
                "x-github-delivery", UUID.randomUUID().toString(),
                "x-hub-signature-256", WebhookSigner.sign(body, WEBHOOK_SECRET)));
    }

    HttpResponse<String> slackAction(String userId, String ticketKey, String escalationId, String decision) {
        String value = "{\\\"ticketKey\\\":\\\"" + ticketKey + "\\\",\\\"escalationId\\\":\\\"" + escalationId
                + "\\\",\\\"decision\\\":\\\"" + decision + "\\\"}";
        String payload = "{\"type\":\"block_actions\",\"user\":{\"id\":\"" + userId + "\"},\"channel\":{\"id\":\"C1\"},"
                + "\"message\":{\"ts\":\"1.1\",\"blocks\":[]},\"actions\":[{\"value\":\"" + value + "\"}]}";
        String body = "payload=" + java.net.URLEncoder.encode(payload, java.nio.charset.StandardCharsets.UTF_8);
        String ts = Long.toString(Instant.now().getEpochSecond());
        return send("POST", api("/slack/actions"), body, Map.of(
                "x-slack-request-timestamp", ts,
                "x-slack-signature", "v0=" + io.agentic.functions.ingress.HmacVerifier.sign("local-slack-secret", "v0:" + ts + ":" + body)));
    }

    String escalationId(String ticketKey) {
        return store.string(ticketKey, "escalationId").orElseThrow();
    }

    void stub(String json) {
        send("POST", WIREMOCK + "/__admin/mappings", json, Map.of("Content-Type", "application/json"));
    }

    int wiremockCount(String method, String urlPathPattern, String bodyContains) {
        String filter = bodyContains == null
                ? "{\"method\":\"" + method + "\",\"urlPathPattern\":\"" + urlPathPattern + "\"}"
                : "{\"method\":\"" + method + "\",\"urlPathPattern\":\"" + urlPathPattern + "\",\"bodyPatterns\":[{\"contains\":\"" + bodyContains + "\"}]}";
        try {
            return Json.MAPPER.readTree(send("POST", WIREMOCK + "/__admin/requests/count", filter, Map.of()).body()).path("count").asInt();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    Run awaitState(String ticketKey, RunState state, Duration timeout) {
        return await(() -> store.get(ticketKey).filter(r -> r.state() == state),
                timeout, () -> "run " + ticketKey + " to reach " + state + " (now " + store.get(ticketKey).map(Run::state).orElse(null) + ")");
    }

    String awaitExecutionStatus(String ticketKey, String status, Duration timeout) {
        await(() -> store.get(ticketKey).map(Run::executionArn)
                        .map(arn -> sfn.describeExecution(DescribeExecutionRequest.builder().executionArn(arn).build()).statusAsString())
                        .filter(status::equals),
                timeout, () -> "execution of " + ticketKey + " to be " + status);
        return status;
    }

    void awaitWait(String ticketKey, String kind, Duration timeout) {
        await(() -> {
            Map<String, AttributeValue> item = ddb.getItem(b -> b.tableName(Tables.RUNS).key(Map.of("ticketKey", AttributeValue.fromS(ticketKey))).consistentRead(true)).item();
            AttributeValue waits = item == null ? null : item.get("waits");
            return waits != null && waits.m() != null && waits.m().containsKey(kind) ? Optional.of(true) : Optional.empty();
        }, timeout, () -> ticketKey + " waiting for " + kind);
    }

    long executionsFor(String ticketKey, Instant since) {
        String machine = sfn.listStateMachines(ListStateMachinesRequest.builder().build()).stateMachines().stream()
                .filter(m -> m.name().equals("agentic-ticket-run")).findFirst().orElseThrow().stateMachineArn();
        List<ExecutionListItem> all = sfn.listExecutions(ListExecutionsRequest.builder().stateMachineArn(machine).maxResults(1000).build()).executions();
        return all.stream().filter(e -> e.name().startsWith(ticketKey + "-")).filter(e -> !e.startDate().isBefore(since)).count();
    }

    static String fixture(String name, Map<String, String> replacements) {
        try (var in = E2eDriver.class.getResourceAsStream("/e2e/" + name)) {
            String text = new String(in.readAllBytes());
            for (Map.Entry<String, String> r : replacements.entrySet()) {
                text = text.replace("${" + r.getKey() + "}", r.getValue());
            }
            return text;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static JsonNode json(String text) {
        try {
            return Json.MAPPER.readTree(text);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private <T> T await(Supplier<Optional<T>> check, Duration timeout, Supplier<String> what) {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            Optional<T> v = check.get();
            if (v.isPresent()) {
                return v.get();
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new AssertionError("Timed out waiting for " + what.get());
    }

    private String api(String path) {
        return "http://localhost:" + server.port() + path;
    }

    private HttpResponse<String> send(String method, String url, String body, Map<String, String> headers) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60));
        headers.forEach(b::header);
        b.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        try {
            return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void close() {
        server.stop();
    }
}
