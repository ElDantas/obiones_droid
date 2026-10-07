package io.agentic.baseline;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.agentic.integrations.http.JsonHttp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.time.Instant;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

class BaselineCollectorTest {
    private WireMockServer wm;

    @BeforeEach
    void start() {
        wm = new WireMockServer(options().dynamicPort());
        wm.start();
    }

    @AfterEach
    void stop() {
        wm.stop();
    }

    @Test
    void collectsLeadTimeReviewRoundsAndRevert() {
        wm.stubFor(post("/rest/api/3/search/jql").willReturn(okJson("""
                {"issues":[{"key":"ABC-1","fields":{"customfield_10016":3}}]}""")));
        wm.stubFor(get(urlPathEqualTo("/rest/api/3/issue/ABC-1/changelog")).willReturn(okJson("""
                {"isLast":true,"values":[
                  {"created":"2026-07-01T10:00:00.000+0100","items":[{"field":"status","toString":"In Progress"}]}]}""")));
        wm.stubFor(get(urlPathEqualTo("/search/issues")).withQueryParam("q", containing("ABC-1")).willReturn(okJson("""
                {"items":[{"number":12},{"number":10}]}""")));
        wm.stubFor(get(urlPathEqualTo("/search/issues")).withQueryParam("q", containing("Revert")).willReturn(okJson("""
                {"items":[{"number":15,"title":"Revert \\"Cap discounts\\"","body":"Reverts #10","created_at":"2026-07-05T09:00:00Z"}]}""")));
        wm.stubFor(get("/repos/acme/pay/pulls/10").willReturn(okJson("""
                {"created_at":"2026-07-01T12:00:00Z","merged_at":"2026-07-02T09:00:00Z"}""")));
        wm.stubFor(get(urlPathEqualTo("/repos/acme/pay/issues/10/timeline")).willReturn(okJson("""
                [{"event":"ready_for_review","created_at":"2026-07-01T15:00:00Z"}]""")));
        wm.stubFor(get(urlPathEqualTo("/repos/acme/pay/pulls/10/reviews")).willReturn(okJson("""
                [{"state":"CHANGES_REQUESTED"},{"state":"APPROVED"}]""")));

        BaselineCollector c = new BaselineCollector(new JsonHttp(HttpClient.newHttpClient(), d -> { }),
                wm.baseUrl(), "e", "t", "customfield_10016", wm.baseUrl(), "gh");
        List<TicketMetrics> tickets = c.collect("project = ABC", List.of("acme/pay"));

        TicketMetrics t = tickets.get(0);
        assertThat(t.inProgressAt()).isEqualTo(Instant.parse("2026-07-01T09:00:00Z"));
        assertThat(t.prReadyAt()).isEqualTo(Instant.parse("2026-07-01T15:00:00Z"));
        assertThat(t.leadTimeHours()).isEqualTo(6.0);
        assertThat(t.reviewRounds()).isEqualTo(2);
        assertThat(t.revertedWithin14d()).isTrue();
        assertThat(t.storyPoints()).isEqualTo(3.0);
    }
}
