package io.agentic.integrations.github;

import io.agentic.core.scope.ScopeGuard.ChangedFile;
import io.agentic.integrations.WireMockSupport;
import io.agentic.integrations.github.model.PullRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Base64;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static org.assertj.core.api.Assertions.assertThat;

class GitHubClientTest extends WireMockSupport {
    private GitHubClient client;

    @BeforeEach
    void setUp() {
        client = new GitHubClient(http, base(), () -> "app-token");
    }

    @Test
    void listFilesMapsFieldsAndFollowsPagination() {
        wm.stubFor(get(urlEqualTo("/repos/acme/pay/pulls/7/files?per_page=100"))
                .willReturn(okJson("[{\"filename\":\"a.java\",\"additions\":3,\"deletions\":1}]")
                        .withHeader("Link", "<" + base() + "/repos/acme/pay/pulls/7/files?per_page=100&page=2>; rel=\"next\"")));
        wm.stubFor(get(urlEqualTo("/repos/acme/pay/pulls/7/files?per_page=100&page=2"))
                .willReturn(okJson("[{\"filename\":\"b.java\",\"additions\":0,\"deletions\":5}]")));
        List<ChangedFile> files = client.listFiles("acme/pay", 7);
        assertThat(files).containsExactly(new ChangedFile("a.java", 3, 1), new ChangedFile("b.java", 0, 5));
    }

    @Test
    void createIssueUsesAppTokenAndReturnsNumber() {
        wm.stubFor(post("/repos/acme/pay/issues").withHeader("Authorization", equalTo("Bearer app-token"))
                .willReturn(okJson("{\"number\":101}")));
        assertThat(client.createIssue("acme/pay", "t", "b")).isEqualTo(101);
    }

    @Test
    void getPullRequestMapsFields() {
        wm.stubFor(get("/repos/acme/pay/pulls/418").willReturn(okJson("""
                {"number":418,"state":"open","draft":true,"merged":false,"head":{"sha":"abc","ref":"copilot/fix-1"},
                 "user":{"login":"Copilot"},"html_url":"https://github.com/acme/pay/pull/418","additions":10,"deletions":2,"changed_files":3,"node_id":"PR_1"}""")));
        PullRequest pr = client.getPullRequest("acme/pay", 418);
        assertThat(pr.headSha()).isEqualTo("abc");
        assertThat(pr.headRef()).isEqualTo("copilot/fix-1");
        assertThat(pr.authorLogin()).isEqualTo("Copilot");
        assertThat(pr.draft()).isTrue();
        assertThat(pr.nodeId()).isEqualTo("PR_1");
    }

    @Test
    void readFileDecodesBase64AndReturnsEmptyOn404() {
        String encoded = Base64.getEncoder().encodeToString("hello".getBytes());
        wm.stubFor(get("/repos/acme/pay/contents/.github/agentic.yml").willReturn(okJson("{\"content\":\"" + encoded + "\"}")));
        wm.stubFor(get("/repos/acme/pay/contents/missing.yml").willReturn(aResponse().withStatus(404)));
        assertThat(client.readFile("acme/pay", ".github/agentic.yml")).contains("hello");
        assertThat(client.readFile("acme/pay", "missing.yml")).isEmpty();
    }

    @Test
    void openPullRequestsForIssueFiltersOpenSameRepo() {
        wm.stubFor(post("/graphql").willReturn(okJson("""
                {"data":{"repository":{"issue":{"timelineItems":{"nodes":[
                  {"__typename":"ConnectedEvent","subject":{"number":418,"state":"OPEN","repository":{"nameWithOwner":"acme/pay"}}},
                  {"__typename":"CrossReferencedEvent","source":{"number":419,"state":"OPEN","repository":{"nameWithOwner":"acme/pay"}}},
                  {"__typename":"CrossReferencedEvent","source":{"number":5,"state":"CLOSED","repository":{"nameWithOwner":"acme/pay"}}},
                  {"__typename":"CrossReferencedEvent","source":{"number":9,"state":"OPEN","repository":{"nameWithOwner":"other/repo"}}}
                ]}}}}}""")));
        assertThat(client.openPullRequestsForIssue("acme/pay", 101)).containsExactly(418, 419);
    }

    @Test
    void markReadyForReviewUsesNodeId() {
        wm.stubFor(get("/repos/acme/pay/pulls/418").willReturn(okJson("{\"number\":418,\"node_id\":\"PR_9\",\"head\":{}}")));
        wm.stubFor(post("/graphql").willReturn(okJson("{\"data\":{}}")));
        client.markReadyForReview("acme/pay", 418);
        wm.verify(postRequestedFor(urlEqualTo("/graphql")).withRequestBody(matchingJsonPath("$.variables.id", equalTo("PR_9"))));
    }

    @Test
    void hasNoMergeCapability() {
        List<String> names = Arrays.stream(GitHubClient.class.getMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .map(Method::getName)
                .toList();
        assertThat(names).noneMatch(n -> n.toLowerCase().contains("merge"));
    }
}
