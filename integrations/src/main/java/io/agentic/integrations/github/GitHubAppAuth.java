package io.agentic.integrations.github;

import com.fasterxml.jackson.databind.JsonNode;
import io.agentic.integrations.http.JsonHttp;
import io.jsonwebtoken.Jwts;

import java.security.PrivateKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.function.Supplier;

public class GitHubAppAuth implements Supplier<String> {
    private final JsonHttp http;
    private final String apiBase;
    private final String appId;
    private final String installationId;
    private final PrivateKey privateKey;
    private final Clock clock;
    private String cached;
    private Instant cachedExpiry;

    public GitHubAppAuth(JsonHttp http, String apiBase, String appId, String installationId, PrivateKey privateKey, Clock clock) {
        this.http = http;
        this.apiBase = apiBase;
        this.appId = appId;
        this.installationId = installationId;
        this.privateKey = privateKey;
        this.clock = clock;
    }

    @Override
    public String get() {
        return installationToken();
    }

    public synchronized String installationToken() {
        Instant now = clock.instant();
        if (cached != null && now.isBefore(cachedExpiry.minus(Duration.ofMinutes(5)))) {
            return cached;
        }
        String jwt = Jwts.builder()
                .issuer(appId)
                .issuedAt(Date.from(now.minusSeconds(60)))
                .expiration(Date.from(now.plus(Duration.ofMinutes(9))))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
        JsonNode res = http.post(apiBase + "/app/installations/" + installationId + "/access_tokens",
                Map.of("Authorization", "Bearer " + jwt, "Accept", "application/vnd.github+json", "User-Agent", "agentic-bot"), Map.of());
        cached = res.get("token").asText();
        cachedExpiry = Instant.parse(res.get("expires_at").asText());
        return cached;
    }
}
