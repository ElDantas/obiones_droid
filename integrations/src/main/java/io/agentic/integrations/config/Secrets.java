package io.agentic.integrations.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import io.agentic.integrations.http.Json;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Secrets {
    private static final Duration TTL = Duration.ofMinutes(5);

    private record Entry(String value, Instant fetchedAt) {
    }

    private final SecretsManagerClient client;
    private final Clock clock;
    private final Map<String, Entry> cache = new ConcurrentHashMap<>();

    public Secrets(SecretsManagerClient client, Clock clock) {
        this.client = client;
        this.clock = clock;
    }

    public String get(String name) {
        Instant now = clock.instant();
        Entry e = cache.get(name);
        if (e != null && now.isBefore(e.fetchedAt().plus(TTL))) {
            return e.value();
        }
        String value = client.getSecretValue(GetSecretValueRequest.builder().secretId(name).build()).secretString();
        cache.put(name, new Entry(value, now));
        return value;
    }

    public JsonNode getJson(String name) {
        try {
            return Json.MAPPER.readTree(get(name));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Secret " + name + " is not valid JSON", e);
        }
    }
}
