package io.agentic.integrations.github;

import io.agentic.integrations.WireMockSupport;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

class GitHubAppAuthTest extends WireMockSupport {

    private static class MutableClock extends Clock {
        final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-07T12:00:00Z"));

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    }

    @Test
    void cachesTokenUntilFiveMinutesBeforeExpiry() {
        MutableClock clock = new MutableClock();
        wm.stubFor(post("/app/installations/42/access_tokens")
                .withHeader("Authorization", matching("Bearer .+"))
                .willReturn(okJson("{\"token\":\"ghs_x\",\"expires_at\":\"2026-10-07T13:00:00Z\"}")));
        GitHubAppAuth auth = new GitHubAppAuth(http, base(), "123", "42", Keys.rsa().getPrivate(), clock);

        assertThat(auth.installationToken()).isEqualTo("ghs_x");
        assertThat(auth.installationToken()).isEqualTo("ghs_x");
        wm.verify(1, postRequestedFor(urlEqualTo("/app/installations/42/access_tokens")));

        clock.now.set(Instant.parse("2026-10-07T12:56:00Z"));
        auth.installationToken();
        wm.verify(2, postRequestedFor(urlEqualTo("/app/installations/42/access_tokens")));
    }
}
