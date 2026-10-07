package io.agentic.integrations.jira;

import io.agentic.integrations.WireMockSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JiraClientTest extends WireMockSupport {
    private static final String ISSUE = """
            {"key":"ABC-123","fields":{
              "summary":"Cap discounts",
              "customfield_10123":"acme/payments",
              "customfield_10016":3,
              "components":[{"name":"orders"}],
              "labels":["Agentic AI Approved"],
              "reporter":{"emailAddress":"rep@corp.com"},
              "assignee":{"emailAddress":"dev@corp.com"},
              "description":{"type":"doc","version":1,"content":[
                {"type":"paragraph","content":[{"type":"text","text":"Discounts must be capped."}]},
                {"type":"heading","attrs":{"level":2},"content":[{"type":"text","text":"Acceptance Criteria"}]},
                {"type":"bulletList","content":[
                  {"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"item1"}]}]},
                  {"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"item2"}]}]}
                ]},
                {"type":"heading","attrs":{"level":2},"content":[{"type":"text","text":"Notes"}]},
                {"type":"paragraph","content":[{"type":"text","text":"ignore me"}]}
              ]}
            }}""";

    private JiraClient client;

    @BeforeEach
    void setUp() {
        client = new JiraClient(http, base(), "svc@corp.com", "tok", new JiraFields("customfield_10123", "none", "customfield_10016"));
        wm.stubFor(get("/rest/api/3/issue/ABC-123").willReturn(okJson(ISSUE)));
        wm.stubFor(get("/rest/api/3/issue/ABC-123/remotelink").willReturn(okJson("""
                [{"application":{"type":"com.atlassian.confluence"},"object":{"url":"https://corp.atlassian.net/wiki/spaces/ENG/pages/1"}},
                 {"application":{},"object":{"url":"https://example.com"}}]""")));
    }

    @Test
    void readsTicketWithAcFromDescriptionHeading() {
        JiraTicket t = client.getTicket("ABC-123");
        assertThat(t.acceptanceCriteria()).isEqualTo("- item1\n- item2");
        assertThat(t.targetRepo()).isEqualTo("acme/payments");
        assertThat(t.storyPoints()).isEqualTo(3.0);
        assertThat(t.components()).containsExactly("orders");
        assertThat(t.assigneeEmail()).isEqualTo("dev@corp.com");
        assertThat(t.confluenceLinks()).containsExactly("https://corp.atlassian.net/wiki/spaces/ENG/pages/1");
        assertThat(t.description()).startsWith("Discounts must be capped.");
    }

    @Test
    void usesBasicAuth() {
        client.getTicket("ABC-123");
        wm.verify(com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor(urlEqualTo("/rest/api/3/issue/ABC-123"))
                .withHeader("Authorization", equalTo("Basic c3ZjQGNvcnAuY29tOnRvaw==")));
    }

    @Test
    void transitionPicksMatchingId() {
        wm.stubFor(get("/rest/api/3/issue/ABC-123/transitions").willReturn(okJson("""
                {"transitions":[{"id":"11","to":{"name":"In Progress"}},{"id":"31","to":{"name":"In Review"}}]}""")));
        wm.stubFor(post("/rest/api/3/issue/ABC-123/transitions").willReturn(okJson("{}")));
        client.transition("ABC-123", "in review");
        wm.verify(postRequestedFor(urlEqualTo("/rest/api/3/issue/ABC-123/transitions"))
                .withRequestBody(matchingJsonPath("$.transition.id", equalTo("31"))));
    }

    @Test
    void missingTransitionThrows() {
        wm.stubFor(get("/rest/api/3/issue/ABC-123/transitions").willReturn(okJson("{\"transitions\":[]}")));
        assertThatThrownBy(() -> client.transition("ABC-123", "Done")).isInstanceOf(JiraTransitionMissing.class);
    }

    @Test
    void commentIsSentAsAdf() {
        wm.stubFor(post("/rest/api/3/issue/ABC-123/comment").willReturn(okJson("{}")));
        client.comment("ABC-123", "line one\nline two");
        wm.verify(postRequestedFor(urlEqualTo("/rest/api/3/issue/ABC-123/comment"))
                .withRequestBody(matchingJsonPath("$.body.type", equalTo("doc")))
                .withRequestBody(matchingJsonPath("$.body.content[1].content[0].text", equalTo("line two"))));
    }
}
