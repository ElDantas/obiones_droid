package io.agentic.functions.digest;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class LedgerReader {
    private final DynamoDbClient ddb;
    private final String table;

    public LedgerReader(DynamoDbClient ddb, String table) {
        this.ddb = ddb;
        this.table = table;
    }

    public List<LedgerRow> week(String isoWeek) {
        List<LedgerRow> rows = new ArrayList<>();
        Map<String, AttributeValue> start = null;
        do {
            QueryRequest.Builder b = QueryRequest.builder().tableName(table).indexName("byWeek")
                    .keyConditionExpression("week = :w")
                    .expressionAttributeValues(Map.of(":w", AttributeValue.fromS(isoWeek)));
            if (start != null) {
                b.exclusiveStartKey(start);
            }
            QueryResponse res = ddb.query(b.build());
            for (Map<String, AttributeValue> i : res.items()) {
                rows.add(new LedgerRow(s(i, "ticketKey"), s(i, "runId"), s(i, "repo"), s(i, "from"), s(i, "to"),
                        Instant.parse(s(i, "ts")), n(i, "premiumRequests"), n(i, "actionsMinutes"), s(i, "reason")));
            }
            start = res.hasLastEvaluatedKey() && !res.lastEvaluatedKey().isEmpty() ? res.lastEvaluatedKey() : null;
        } while (start != null);
        return rows;
    }

    private static String s(Map<String, AttributeValue> i, String k) {
        return i.containsKey(k) ? i.get(k).s() : null;
    }

    private static int n(Map<String, AttributeValue> i, String k) {
        return i.containsKey(k) && i.get(k).n() != null ? Integer.parseInt(i.get(k).n()) : 0;
    }
}
