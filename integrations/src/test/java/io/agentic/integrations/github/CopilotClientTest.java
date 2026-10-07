package io.agentic.integrations.github;

import io.agentic.integrations.WireMockSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CopilotClientTest extends WireMockSupport {
    private CopilotClient client;

    @BeforeEach
    void setUp() {
        client = new CopilotClient(http, base(), () -> "svc-token");
    }

    @Test
    void assignsUsingBotIdFromSuggestedActors() {
        wm.stubFor(post("/graphql").withRequestBody(containing("suggestedActors")).willReturn(okJson("""
                {"data":{"repository":{"issue":{"id":"I_1"},"suggestedActors":{"nodes":[
                  {"login":"alice","__typename":"User"},
                  {"login":"copilot-swe-agent","__typename":"Bot","id":"BOT_7"}]}}}}""")));
        wm.stubFor(post("/graphql").withRequestBody(containing("replaceActorsForAssignable")).willReturn(okJson("{\"data\":{}}")));

        client.assignCopilot("acme/pay", 101);

        wm.verify(postRequestedFor(urlEqualTo("/graphql"))
                .withHeader("Authorization", equalTo("Bearer svc-token"))
                .withRequestBody(matchingJsonPath("$.variables.ids[0]", equalTo("BOT_7")))
                .withRequestBody(matchingJsonPath("$.variables.a", equalTo("I_1"))));
    }

    @Test
    void throwsWhenCopilotIsNotAssignable() {
        wm.stubFor(post("/graphql").willReturn(okJson("""
                {"data":{"repository":{"issue":{"id":"I_1"},"suggestedActors":{"nodes":[{"login":"alice","__typename":"User"}]}}}}""")));
        assertThatThrownBy(() -> client.assignCopilot("acme/pay", 101)).isInstanceOf(CopilotUnavailableException.class);
    }

    @Test
    void instructPrefixesMentionAndUsesServiceUserToken() {
        wm.stubFor(post("/repos/acme/pay/issues/418/comments").willReturn(okJson("{}")));
        client.instruct("acme/pay", 418, "fix x");
        wm.verify(postRequestedFor(urlEqualTo("/repos/acme/pay/issues/418/comments"))
                .withHeader("Authorization", equalTo("Bearer svc-token"))
                .withRequestBody(matchingJsonPath("$.body", equalTo("@copilot fix x"))));
    }

    @Test
    void instructKeepsExistingMention() {
        wm.stubFor(post("/repos/acme/pay/issues/418/comments").willReturn(okJson("{}")));
        client.instruct("acme/pay", 418, "@copilot please fix");
        wm.verify(postRequestedFor(urlEqualTo("/repos/acme/pay/issues/418/comments"))
                .withRequestBody(matchingJsonPath("$.body", equalTo("@copilot please fix"))));
    }
}
