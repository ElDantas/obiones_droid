package io.agentic.functions.knowledge;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.agentic.functions.config.Params;
import io.agentic.integrations.confluence.ConfluenceClient;
import io.agentic.integrations.http.JsonHttp;
import io.agentic.memory.LessonDraft;
import io.agentic.memory.LessonRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConfluenceSyncJobTest {
    private WireMockServer wm;

    @BeforeEach
    void start() {
        wm = new WireMockServer(options().dynamicPort());
        wm.start();
        wm.stubFor(get(urlPathEqualTo("/wiki/rest/api/content/search")).willReturn(okJson("""
                {"results":[{"id":"42","title":"ADR-014 Event sourcing for payments","version":{"number":3},
                  "_links":{"webui":"/spaces/ENG/pages/42"},
                  "metadata":{"labels":{"results":[{"name":"adr"}]}},
                  "body":{"storage":{"value":"<h1>Decision</h1><p>Payments use event sourcing.</p>"}}}],
                 "_links":{}}""")));
    }

    @AfterEach
    void stop() {
        wm.stop();
    }

    @Test
    void resyncUpdatesSameSourceRefAndAdvancesLastSync() {
        LessonRepository repo = mock(LessonRepository.class);
        Params params = mock(Params.class);
        when(params.find("/agentic/confluence/syncSpaces")).thenReturn(Optional.of("[\"ENG\"]"));
        when(params.find("/agentic/confluence/syncLabels")).thenReturn(Optional.of("[\"adr\",\"spec\"]"));
        when(params.find("/agentic/confluence/lastSync")).thenReturn(Optional.of("2026-10-01 00:00"));
        Map<String, String> saved = new HashMap<>();
        ConfluenceClient client = new ConfluenceClient(new JsonHttp(HttpClient.newHttpClient(), d -> { }), wm.baseUrl(), "e", "t");
        ConfluenceSyncJob job = new ConfluenceSyncJob(client, repo, params, saved::put, Clock.fixed(Instant.parse("2026-10-07T02:00:00Z"), ZoneOffset.UTC));

        job.handleRequest(Map.of(), null);
        job.handleRequest(Map.of(), null);

        verify(repo, times(2)).upsertBySource(eq("confluence:42:0"), argThat((LessonDraft d) ->
                d.kind().equals("adr") && d.scope().equals("shared") && d.lesson().contains("Payments use event sourcing")
                        && d.evidence().endsWith("/wiki/spaces/ENG/pages/42")));
        assertThat(saved).containsEntry("/agentic/confluence/lastSync", "2026-10-07 02:00");
        wm.verify(getRequestedFor(urlPathEqualTo("/wiki/rest/api/content/search")).withQueryParam("cql", containing("lastmodified >= \"2026-10-01 00:00\"")));
    }

    @Test
    void noSpacesConfiguredDoesNothing() {
        Params params = mock(Params.class);
        when(params.find("/agentic/confluence/syncSpaces")).thenReturn(Optional.of("[]"));
        assertThat(new ConfluenceSyncJob(mock(ConfluenceClient.class), mock(LessonRepository.class), params, (a, b) -> { }, Clock.systemUTC())
                .handleRequest(Map.of(), null)).containsEntry("pages", 0);
    }
}
