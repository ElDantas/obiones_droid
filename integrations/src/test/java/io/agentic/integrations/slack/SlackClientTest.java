package io.agentic.integrations.slack;

import io.agentic.integrations.WireMockSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SlackClientTest extends WireMockSupport {
    private SlackClient client;

    @BeforeEach
    void setUp() {
        client = new SlackClient(http, base() + "/api", "xoxb-test");
    }

    @Test
    void postReturnsTsAndSendsThread() {
        wm.stubFor(post("/api/chat.postMessage").willReturn(okJson("{\"ok\":true,\"ts\":\"1.2\"}")));
        assertThat(client.post("#agentic-dev", "0.9", List.of(), "hi")).isEqualTo("1.2");
        wm.verify(postRequestedFor(urlEqualTo("/api/chat.postMessage"))
                .withHeader("Authorization", equalTo("Bearer xoxb-test"))
                .withRequestBody(matchingJsonPath("$.thread_ts", equalTo("0.9"))));
    }

    @Test
    void notOkThrows() {
        wm.stubFor(post("/api/chat.postMessage").willReturn(okJson("{\"ok\":false,\"error\":\"channel_not_found\"}")));
        assertThatThrownBy(() -> client.post("#x", null, List.of(), "hi")).isInstanceOf(SlackFailure.class).hasMessageContaining("channel_not_found");
    }

    @Test
    void dmLooksUpUserAndOpensConversation() {
        wm.stubFor(get(urlPathEqualTo("/api/users.lookupByEmail")).willReturn(okJson("{\"ok\":true,\"user\":{\"id\":\"U1\"}}")));
        wm.stubFor(post("/api/conversations.open").willReturn(okJson("{\"ok\":true,\"channel\":{\"id\":\"D1\"}}")));
        wm.stubFor(post("/api/chat.postMessage").willReturn(okJson("{\"ok\":true,\"ts\":\"3.4\"}")));
        client.dmByEmail("dev@corp.com", List.of(), "hello");
        wm.verify(postRequestedFor(urlEqualTo("/api/chat.postMessage")).withRequestBody(matchingJsonPath("$.channel", equalTo("D1"))));
    }
}
