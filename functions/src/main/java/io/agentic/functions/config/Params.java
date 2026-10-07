package io.agentic.functions.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.agentic.integrations.http.Json;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.ParameterNotFoundException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class Params {
    private static final Duration TTL = Duration.ofSeconds(30);

    private record Entry(Optional<String> value, Instant fetchedAt) {
    }

    private final SsmClient ssm;
    private final Clock clock;
    private final Map<String, Entry> cache = new ConcurrentHashMap<>();

    public Params(SsmClient ssm, Clock clock) {
        this.ssm = ssm;
        this.clock = clock;
    }

    public String get(String name) {
        return find(name).orElseThrow(() -> new IllegalStateException("Missing SSM parameter " + name));
    }

    public Optional<String> find(String name) {
        Instant now = clock.instant();
        Entry e = cache.get(name);
        if (e != null && now.isBefore(e.fetchedAt().plus(TTL))) {
            return e.value();
        }
        Optional<String> value;
        try {
            value = Optional.of(ssm.getParameter(GetParameterRequest.builder().name(name).build()).parameter().value());
        } catch (ParameterNotFoundException ex) {
            value = Optional.empty();
        }
        cache.put(name, new Entry(value, now));
        return value;
    }

    public <T> T getJson(String name, Class<T> type) {
        try {
            return Json.MAPPER.readValue(get(name), type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("SSM parameter " + name + " is not valid JSON for " + type.getSimpleName(), e);
        }
    }

    public int getInt(String name, int fallback) {
        return find(name).map(String::trim).map(Integer::parseInt).orElse(fallback);
    }

    public boolean isGloballyEnabled() {
        return find("/agentic/enabled").map(v -> v.trim().equalsIgnoreCase("true")).orElse(false);
    }

    public boolean isEnabled(String repo) {
        if (!isGloballyEnabled()) {
            return false;
        }
        return find("/agentic/repos/" + repo + "/enabled").map(v -> v.trim().equalsIgnoreCase("true")).orElse(false);
    }
}
