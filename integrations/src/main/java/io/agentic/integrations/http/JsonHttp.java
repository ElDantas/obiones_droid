package io.agentic.integrations.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JsonHttp {
    private static final Set<Integer> RETRYABLE = Set.of(429, 502, 503, 504);
    private static final List<Duration> BACKOFF = List.of(Duration.ofMillis(250), Duration.ofSeconds(1), Duration.ofSeconds(4));
    private static final Pattern NEXT_LINK = Pattern.compile("<([^>]+)>;\\s*rel=\"next\"");

    public interface Sleeper {
        void sleep(Duration d) throws InterruptedException;
    }

    public record Response(int status, JsonNode body, Map<String, List<String>> headers) {
        public Optional<String> header(String name) {
            for (Map.Entry<String, List<String>> e : headers.entrySet()) {
                if (e.getKey() != null && e.getKey().equalsIgnoreCase(name) && !e.getValue().isEmpty()) {
                    return Optional.of(e.getValue().get(0));
                }
            }
            return Optional.empty();
        }
    }

    private final HttpClient client;
    private final Sleeper sleeper;

    public JsonHttp() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), d -> Thread.sleep(d.toMillis()));
    }

    public JsonHttp(HttpClient client, Sleeper sleeper) {
        this.client = client;
        this.sleeper = sleeper;
    }

    public JsonNode get(String url, Map<String, String> headers) {
        return send("GET", url, headers, null).body();
    }

    public JsonNode post(String url, Map<String, String> headers, Object body) {
        return send("POST", url, headers, body).body();
    }

    public JsonNode put(String url, Map<String, String> headers, Object body) {
        return send("PUT", url, headers, body).body();
    }

    public JsonNode patch(String url, Map<String, String> headers, Object body) {
        return send("PATCH", url, headers, body).body();
    }

    public JsonNode delete(String url, Map<String, String> headers) {
        return send("DELETE", url, headers, null).body();
    }

    public List<JsonNode> getPaged(String url, Map<String, String> headers) {
        List<JsonNode> items = new ArrayList<>();
        String next = url;
        while (next != null) {
            Response r = send("GET", next, headers, null);
            r.body().forEach(items::add);
            next = r.header("Link").map(JsonHttp::nextLink).orElse(null);
        }
        return items;
    }

    public Response send(String method, String url, Map<String, String> headers, Object body) {
        String payload = body == null ? null : write(body);
        for (int attempt = 0; ; attempt++) {
            HttpResponse<String> res = execute(method, url, headers, payload);
            int status = res.statusCode();
            if (status >= 200 && status < 300) {
                return new Response(status, parse(res.body()), res.headers().map());
            }
            if (RETRYABLE.contains(status) && attempt < BACKOFF.size()) {
                pause(BACKOFF.get(attempt));
                continue;
            }
            throw new HttpFailure(status, method, url, res.body());
        }
    }

    static String nextLink(String linkHeader) {
        Matcher m = NEXT_LINK.matcher(linkHeader);
        return m.find() ? m.group(1) : null;
    }

    private HttpResponse<String> execute(String method, String url, Map<String, String> headers, String payload) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30));
        headers.forEach(b::header);
        if (payload != null) {
            b.header("Content-Type", "application/json");
            b.method(method, HttpRequest.BodyPublishers.ofString(payload));
        } else {
            b.method(method, HttpRequest.BodyPublishers.noBody());
        }
        try {
            return client.send(b.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new HttpFailure(0, method, url, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new HttpFailure(0, method, url, "interrupted");
        }
    }

    private void pause(Duration d) {
        try {
            sleeper.sleep(d);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String write(Object body) {
        if (body instanceof String s) {
            return s;
        }
        try {
            return Json.MAPPER.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static JsonNode parse(String text) {
        if (text == null || text.isBlank()) {
            return NullNode.getInstance();
        }
        try {
            return Json.MAPPER.readTree(text);
        } catch (JsonProcessingException e) {
            return Json.MAPPER.getNodeFactory().textNode(text);
        }
    }
}
