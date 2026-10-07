package io.agentic.integrations.http;

import com.fasterxml.jackson.databind.JsonNode;
import io.agentic.integrations.WireMockSupport;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonHttpTest extends WireMockSupport {

    @Test
    void retriesOn503ThenSucceeds() {
        wm.stubFor(get("/x").inScenario("r").whenScenarioStateIs(STARTED).willReturn(aResponse().withStatus(503)).willSetStateTo("ok"));
        wm.stubFor(get("/x").inScenario("r").whenScenarioStateIs("ok").willReturn(okJson("{\"a\":1}")));
        JsonNode res = http.get(base() + "/x", Map.of());
        assertThat(res.get("a").asInt()).isEqualTo(1);
        wm.verify(2, getRequestedFor(urlEqualTo("/x")));
        assertThat(sleeps).hasSize(1);
    }

    @Test
    void doesNotRetryOn400() {
        wm.stubFor(get("/bad").willReturn(aResponse().withStatus(400).withBody("nope")));
        assertThatThrownBy(() -> http.get(base() + "/bad", Map.of()))
                .isInstanceOf(HttpFailure.class)
                .satisfies(e -> assertThat(((HttpFailure) e).status()).isEqualTo(400));
        wm.verify(1, getRequestedFor(urlEqualTo("/bad")));
        assertThat(sleeps).isEmpty();
    }

    @Test
    void givesUpAfterThreeRetries() {
        wm.stubFor(get("/down").willReturn(aResponse().withStatus(502)));
        assertThatThrownBy(() -> http.get(base() + "/down", Map.of())).isInstanceOf(HttpFailure.class);
        wm.verify(4, getRequestedFor(urlEqualTo("/down")));
    }

    @Test
    void followsLinkHeaderPagination() {
        wm.stubFor(get(urlPathEqualTo("/items")).withQueryParam("page", com.github.tomakehurst.wiremock.client.WireMock.absent())
                .willReturn(okJson("[1,2]").withHeader("Link", "<" + base() + "/items?page=2>; rel=\"next\", <" + base() + "/items?page=2>; rel=\"last\"")));
        wm.stubFor(get(urlEqualTo("/items?page=2")).willReturn(okJson("[3]")));
        List<JsonNode> all = http.getPaged(base() + "/items", Map.of());
        assertThat(all).extracting(JsonNode::asInt).containsExactly(1, 2, 3);
    }
}
