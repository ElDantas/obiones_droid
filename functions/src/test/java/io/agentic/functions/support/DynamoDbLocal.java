package io.agentic.functions.support;

import io.agentic.functions.store.Tables;
import org.testcontainers.containers.GenericContainer;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.DeleteTableRequest;

import java.net.URI;

public final class DynamoDbLocal {
    private static GenericContainer<?> container;

    private DynamoDbLocal() {
    }

    public static synchronized DynamoDbClient freshClient() {
        if (container == null) {
            container = new GenericContainer<>("amazon/dynamodb-local:latest")
                    .withCommand("-jar", "DynamoDBLocal.jar", "-inMemory", "-sharedDb")
                    .withExposedPorts(8000);
            container.start();
        }
        DynamoDbClient client = DynamoDbClient.builder()
                .endpointOverride(URI.create("http://" + container.getHost() + ":" + container.getMappedPort(8000)))
                .region(Region.EU_WEST_2)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("local", "local")))
                .build();
        for (String t : new String[]{Tables.RUNS, Tables.LEDGER, Tables.DELIVERIES}) {
            try {
                client.deleteTable(DeleteTableRequest.builder().tableName(t).build());
            } catch (Exception ignored) {
                continue;
            }
        }
        Tables.createAll(client);
        return client;
    }
}
