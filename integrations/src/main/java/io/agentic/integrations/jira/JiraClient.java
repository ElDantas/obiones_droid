package io.agentic.integrations.jira;

import com.fasterxml.jackson.databind.JsonNode;
import io.agentic.integrations.http.JsonHttp;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class JiraClient {
    private static final Pattern AC_HEADING = Pattern.compile("(?i)acceptance criteria");

    private final JsonHttp http;
    private final String baseUrl;
    private final String auth;
    private final JiraFields fields;

    public JiraClient(JsonHttp http, String baseUrl, String email, String apiToken, JiraFields fields) {
        this.http = http;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.auth = "Basic " + Base64.getEncoder().encodeToString((email + ":" + apiToken).getBytes(StandardCharsets.UTF_8));
        this.fields = fields;
    }

    public String browseUrl(String key) {
        return baseUrl + "/browse/" + key;
    }

    public JiraTicket getTicket(String key) {
        JsonNode issue = http.get(baseUrl + "/rest/api/3/issue/" + key, headers());
        JsonNode f = issue.path("fields");
        JsonNode descriptionAdf = f.path("description");
        String description = AdfText.flatten(descriptionAdf);
        String ac;
        if (fields.hasAcceptanceCriteriaField()) {
            ac = AdfText.flatten(f.path(fields.acceptanceCriteria()));
        } else {
            ac = AdfText.section(descriptionAdf, AC_HEADING).orElse("");
        }
        String targetRepo = f.path(fields.targetRepo()).isTextual() ? f.path(fields.targetRepo()).asText() : null;
        JsonNode points = f.path(fields.storyPoints());
        Double storyPoints = points.isNumber() ? points.asDouble() : null;
        List<String> components = new ArrayList<>();
        f.path("components").forEach(c -> components.add(c.path("name").asText()));
        List<String> labels = new ArrayList<>();
        f.path("labels").forEach(l -> labels.add(l.asText()));
        return new JiraTicket(
                issue.path("key").asText(key),
                f.path("summary").asText(""),
                description,
                ac,
                targetRepo,
                storyPoints,
                components,
                labels,
                f.at("/reporter/emailAddress").asText(null),
                f.at("/assignee/emailAddress").asText(null),
                confluenceLinks(key));
    }

    public List<String> searchKeys(String jql) {
        List<String> keys = new ArrayList<>();
        String token = null;
        do {
            Map<String, Object> body = new java.util.HashMap<>();
            body.put("jql", jql);
            body.put("fields", List.of("summary"));
            body.put("maxResults", 100);
            if (token != null) {
                body.put("nextPageToken", token);
            }
            JsonNode res = http.post(baseUrl + "/rest/api/3/search/jql", headers(), body);
            res.path("issues").forEach(i -> keys.add(i.path("key").asText()));
            token = res.hasNonNull("nextPageToken") ? res.get("nextPageToken").asText() : null;
        } while (token != null);
        return keys;
    }

    public String resolution(String key) {
        return http.get(baseUrl + "/rest/api/3/issue/" + key + "?fields=resolution", headers()).at("/fields/resolution/name").asText("");
    }

    public void transition(String key, String statusName) {
        JsonNode res = http.get(baseUrl + "/rest/api/3/issue/" + key + "/transitions", headers());
        for (JsonNode t : res.path("transitions")) {
            if (statusName.equalsIgnoreCase(t.at("/to/name").asText())) {
                http.post(baseUrl + "/rest/api/3/issue/" + key + "/transitions", headers(),
                        Map.of("transition", Map.of("id", t.path("id").asText())));
                return;
            }
        }
        throw new JiraTransitionMissing(key, statusName);
    }

    public void comment(String key, String text) {
        List<Map<String, Object>> paragraphs = new ArrayList<>();
        for (String line : text.split("\n")) {
            paragraphs.add(line.isEmpty()
                    ? Map.of("type", "paragraph", "content", List.of())
                    : Map.of("type", "paragraph", "content", List.of(Map.of("type", "text", "text", line))));
        }
        Map<String, Object> doc = Map.of("type", "doc", "version", 1, "content", paragraphs);
        http.post(baseUrl + "/rest/api/3/issue/" + key + "/comment", headers(), Map.of("body", doc));
    }

    public void removeLabel(String key, String label) {
        http.put(baseUrl + "/rest/api/3/issue/" + key, headers(),
                Map.of("update", Map.of("labels", List.of(Map.of("remove", label)))));
    }

    private List<String> confluenceLinks(String key) {
        JsonNode links = http.get(baseUrl + "/rest/api/3/issue/" + key + "/remotelink", headers());
        List<String> urls = new ArrayList<>();
        for (JsonNode l : links) {
            String url = l.at("/object/url").asText("");
            String app = l.at("/application/type").asText("");
            if (app.equals("com.atlassian.confluence") || url.contains("/wiki/")) {
                urls.add(url);
            }
        }
        return urls;
    }

    private Map<String, String> headers() {
        return Map.of("Authorization", auth, "Accept", "application/json");
    }
}
