package io.agentic.baseline;

import com.fasterxml.jackson.databind.JsonNode;
import io.agentic.integrations.http.JsonHttp;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class BaselineCollector {
    private final JsonHttp http;
    private final String jiraBase;
    private final String jiraAuth;
    private final String storyPointsField;
    private final String githubBase;
    private final String githubToken;

    public BaselineCollector(JsonHttp http, String jiraBase, String jiraEmail, String jiraToken, String storyPointsField,
                             String githubBase, String githubToken) {
        this.http = http;
        this.jiraBase = jiraBase;
        this.jiraAuth = "Basic " + Base64.getEncoder().encodeToString((jiraEmail + ":" + jiraToken).getBytes(StandardCharsets.UTF_8));
        this.storyPointsField = storyPointsField;
        this.githubBase = githubBase;
        this.githubToken = githubToken;
    }

    public List<TicketMetrics> collect(String jql, List<String> repos) {
        List<TicketMetrics> result = new ArrayList<>();
        for (JsonNode issue : searchIssues(jql)) {
            String key = issue.path("key").asText();
            JsonNode points = issue.at("/fields/" + storyPointsField);
            Double storyPoints = points.isNumber() ? points.asDouble() : null;
            Instant inProgressAt = firstInProgress(key).orElse(null);
            Optional<PrRef> pr = findPr(key, repos);
            if (pr.isEmpty()) {
                result.add(new TicketMetrics(key, null, storyPoints, inProgressAt, null, null, 0, false));
                continue;
            }
            result.add(prMetrics(key, storyPoints, inProgressAt, pr.get()));
        }
        return result;
    }

    private record PrRef(String repo, int number) {
    }

    private List<JsonNode> searchIssues(String jql) {
        List<JsonNode> issues = new ArrayList<>();
        String token = null;
        do {
            Map<String, Object> body = new HashMap<>();
            body.put("jql", jql);
            body.put("fields", List.of("summary", storyPointsField));
            body.put("maxResults", 100);
            if (token != null) {
                body.put("nextPageToken", token);
            }
            JsonNode res = http.post(jiraBase + "/rest/api/3/search/jql", jiraHeaders(), body);
            res.path("issues").forEach(issues::add);
            token = res.hasNonNull("nextPageToken") ? res.get("nextPageToken").asText() : null;
        } while (token != null);
        return issues;
    }

    private Optional<Instant> firstInProgress(String key) {
        Instant first = null;
        int startAt = 0;
        while (true) {
            JsonNode res = http.get(jiraBase + "/rest/api/3/issue/" + key + "/changelog?startAt=" + startAt + "&maxResults=100", jiraHeaders());
            for (JsonNode history : res.path("values")) {
                for (JsonNode item : history.path("items")) {
                    if ("status".equals(item.path("field").asText()) && "In Progress".equalsIgnoreCase(item.path("toString").asText())) {
                        Instant at = java.time.OffsetDateTime.parse(normalise(history.path("created").asText())).toInstant();
                        if (first == null || at.isBefore(first)) {
                            first = at;
                        }
                    }
                }
            }
            if (res.path("isLast").asBoolean(true)) {
                break;
            }
            startAt += res.path("maxResults").asInt(100);
        }
        return Optional.ofNullable(first);
    }

    private Optional<PrRef> findPr(String key, List<String> repos) {
        PrRef best = null;
        for (String repo : repos) {
            String q = URLEncoder.encode("repo:" + repo + " is:pr " + key, StandardCharsets.UTF_8);
            JsonNode res = http.get(githubBase + "/search/issues?q=" + q, githubHeaders());
            for (JsonNode item : res.path("items")) {
                int n = item.path("number").asInt();
                if (best == null || n < best.number()) {
                    best = new PrRef(repo, n);
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private TicketMetrics prMetrics(String key, Double storyPoints, Instant inProgressAt, PrRef ref) {
        String prPath = githubBase + "/repos/" + ref.repo() + "/pulls/" + ref.number();
        JsonNode pr = http.get(prPath, githubHeaders());
        Instant created = Instant.parse(pr.path("created_at").asText());
        Instant mergedAt = pr.hasNonNull("merged_at") ? Instant.parse(pr.get("merged_at").asText()) : null;
        Instant readyAt = created;
        for (JsonNode e : http.getPaged(githubBase + "/repos/" + ref.repo() + "/issues/" + ref.number() + "/timeline?per_page=100", githubHeaders())) {
            if ("ready_for_review".equals(e.path("event").asText())) {
                readyAt = Instant.parse(e.path("created_at").asText());
            }
        }
        int changesRequested = 0;
        for (JsonNode r : http.getPaged(prPath + "/reviews?per_page=100", githubHeaders())) {
            if ("CHANGES_REQUESTED".equals(r.path("state").asText())) {
                changesRequested++;
            }
        }
        boolean reverted = mergedAt != null && reverted(ref, mergedAt);
        return new TicketMetrics(key, ref.repo(), storyPoints, inProgressAt, readyAt, mergedAt, changesRequested + 1, reverted);
    }

    private boolean reverted(PrRef ref, Instant mergedAt) {
        String q = URLEncoder.encode("repo:" + ref.repo() + " is:pr in:title Revert \"#" + ref.number() + "\"", StandardCharsets.UTF_8);
        JsonNode res = http.get(githubBase + "/search/issues?q=" + q, githubHeaders());
        for (JsonNode item : res.path("items")) {
            Instant created = Instant.parse(item.path("created_at").asText());
            boolean mentions = item.path("body").asText("").contains("#" + ref.number()) || item.path("title").asText("").contains("#" + ref.number());
            if (item.path("title").asText("").startsWith("Revert \"") && mentions
                    && !created.isBefore(mergedAt) && created.isBefore(mergedAt.plus(Duration.ofDays(14)))) {
                return true;
            }
        }
        return false;
    }

    private static String normalise(String jiraTimestamp) {
        return jiraTimestamp.replaceFirst("([+-]\\d{2})(\\d{2})$", "$1:$2").replaceFirst("\\.\\d{3}", "");
    }

    private Map<String, String> jiraHeaders() {
        return Map.of("Authorization", jiraAuth, "Accept", "application/json");
    }

    private Map<String, String> githubHeaders() {
        return Map.of("Authorization", "Bearer " + githubToken, "Accept", "application/vnd.github+json", "User-Agent", "agentic-baseline");
    }
}
