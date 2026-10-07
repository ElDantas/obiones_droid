package io.agentic.functions.store;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.GlobalSecondaryIndex;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.Projection;
import software.amazon.awssdk.services.dynamodb.model.ProjectionType;
import software.amazon.awssdk.services.dynamodb.model.ResourceInUseException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

import java.util.List;

public final class Tables {
    public static final String RUNS = "agentic-runs";
    public static final String LEDGER = "agentic-ledger";
    public static final String DELIVERIES = "agentic-deliveries";

    private Tables() {
    }

    public static void createAll(DynamoDbClient ddb) {
        create(ddb, CreateTableRequest.builder()
                .tableName(RUNS)
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .attributeDefinitions(attr("ticketKey"), attr("repoIssue"), attr("repoPr"))
                .keySchema(hash("ticketKey"))
                .globalSecondaryIndexes(gsi("byIssue", "repoIssue", null), gsi("byPr", "repoPr", null))
                .build());
        create(ddb, CreateTableRequest.builder()
                .tableName(LEDGER)
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .attributeDefinitions(attr("ticketKey"), attr("tsSeq"), attr("week"), attr("ts"))
                .keySchema(hash("ticketKey"), range("tsSeq"))
                .globalSecondaryIndexes(gsi("byWeek", "week", "ts"))
                .build());
        create(ddb, CreateTableRequest.builder()
                .tableName(DELIVERIES)
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .attributeDefinitions(attr("deliveryId"))
                .keySchema(hash("deliveryId"))
                .build());
    }

    private static void create(DynamoDbClient ddb, CreateTableRequest req) {
        try {
            ddb.createTable(req);
        } catch (ResourceInUseException ignored) {
            return;
        }
    }

    private static AttributeDefinition attr(String name) {
        return AttributeDefinition.builder().attributeName(name).attributeType(ScalarAttributeType.S).build();
    }

    private static KeySchemaElement hash(String name) {
        return KeySchemaElement.builder().attributeName(name).keyType(KeyType.HASH).build();
    }

    private static KeySchemaElement range(String name) {
        return KeySchemaElement.builder().attributeName(name).keyType(KeyType.RANGE).build();
    }

    private static GlobalSecondaryIndex gsi(String name, String hashKey, String rangeKey) {
        List<KeySchemaElement> keys = rangeKey == null ? List.of(hash(hashKey)) : List.of(hash(hashKey), range(rangeKey));
        return GlobalSecondaryIndex.builder()
                .indexName(name)
                .keySchema(keys)
                .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                .build();
    }
}
