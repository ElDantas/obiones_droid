package io.agentic.integrations.github;

import com.fasterxml.jackson.databind.JsonNode;
import io.agentic.integrations.http.HttpFailure;
import io.agentic.integrations.http.JsonHttp;

import java.util.Map;
import java.util.function.Supplier;

class GitHubApi {
    final JsonHttp http;
    final String apiBase;
    private final Supplier<String> token;

    GitHubApi(JsonHttp http, String apiBase, Supplier<String> token) {
        this.http = http;
        this.apiBase = apiBase.endsWith("/") ? apiBase.substring(0, apiBase.length() - 1) : apiBase;
        this.token = token;
    }

    Map<String, String> headers() {
        return Map.of(
                "Authorization", "Bearer " + token.get(),
                "Accept", "application/vnd.github+json",
                "X-GitHub-Api-Version", "2022-11-28",
                "User-Agent", "agentic-bot");
    }

    String url(String path) {
        return apiBase + path;
    }

    JsonNode graphql(String query, Map<String, Object> variables) {
        JsonNode res = http.post(apiBase + "/graphql", headers(), Map.of("query", query, "variables", variables));
        JsonNode errors = res.get("errors");
        if (errors != null && errors.isArray() && !errors.isEmpty()) {
            throw new HttpFailure(200, "POST", apiBase + "/graphql", errors.toString());
        }
        return res;
    }
}
