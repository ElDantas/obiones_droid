package io.agentic.integrations.confluence;

import com.fasterxml.jackson.databind.JsonNode;
import io.agentic.integrations.http.JsonHttp;
import org.jsoup.Jsoup;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ConfluenceClient {
    public record Page(String id, String title, String url, int version, String bodyStorage, List<String> labels) {
        public String text() {
            return Jsoup.parse(bodyStorage == null ? "" : bodyStorage).wholeText().replaceAll("\n{3,}", "\n\n").strip();
        }
    }

    private final JsonHttp http;
    private final String baseUrl;
    private final String auth;

    public ConfluenceClient(JsonHttp http, String baseUrl, String email, String apiToken) {
        this.http = http;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.auth = "Basic " + Base64.getEncoder().encodeToString((email + ":" + apiToken).getBytes(StandardCharsets.UTF_8));
    }

    public List<Page> searchPages(String cql) {
        List<Page> pages = new ArrayList<>();
        String next = "/wiki/rest/api/content/search?limit=50&expand=body.storage,version,metadata.labels&cql=" + URLEncoder.encode(cql, StandardCharsets.UTF_8);
        while (next != null) {
            JsonNode res = http.get(baseUrl + next, headers());
            for (JsonNode p : res.path("results")) {
                List<String> labels = new ArrayList<>();
                p.at("/metadata/labels/results").forEach(l -> labels.add(l.path("name").asText()));
                pages.add(new Page(p.path("id").asText(), p.path("title").asText(),
                        baseUrl + "/wiki" + p.at("/_links/webui").asText(""),
                        p.at("/version/number").asInt(1), p.at("/body/storage/value").asText(""), labels));
            }
            next = res.at("/_links/next").isMissingNode() || res.at("/_links/next").isNull() ? null : res.at("/_links/next").asText();
        }
        return pages;
    }

    public String createOrUpdatePage(String spaceKey, String parentId, String title, String storageBody) {
        Optional<JsonNode> existing = findByTitle(spaceKey, title);
        if (existing.isPresent()) {
            String id = existing.get().path("id").asText();
            int version = existing.get().at("/version/number").asInt(1) + 1;
            http.put(baseUrl + "/wiki/rest/api/content/" + id, headers(), Map.of(
                    "id", id, "type", "page", "title", title,
                    "version", Map.of("number", version),
                    "body", Map.of("storage", Map.of("value", storageBody, "representation", "storage"))));
            return id;
        }
        JsonNode created = http.post(baseUrl + "/wiki/rest/api/content", headers(), Map.of(
                "type", "page", "title", title,
                "space", Map.of("key", spaceKey),
                "ancestors", List.of(Map.of("id", parentId)),
                "body", Map.of("storage", Map.of("value", storageBody, "representation", "storage"))));
        return created.path("id").asText();
    }

    private Optional<JsonNode> findByTitle(String spaceKey, String title) {
        JsonNode res = http.get(baseUrl + "/wiki/rest/api/content?spaceKey=" + URLEncoder.encode(spaceKey, StandardCharsets.UTF_8)
                + "&title=" + URLEncoder.encode(title, StandardCharsets.UTF_8) + "&expand=version", headers());
        JsonNode results = res.path("results");
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    private Map<String, String> headers() {
        return Map.of("Authorization", auth, "Accept", "application/json");
    }
}
