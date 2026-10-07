package io.agentic.local.e2e;

import io.agentic.core.run.RunState;
import io.agentic.integrations.llm.FakeLlm;
import io.agentic.memory.JdbcExecutor;
import io.agentic.memory.LessonDraft;
import io.agentic.memory.LessonRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryE2E {
    private static final Duration T = Duration.ofSeconds(180);
    private static final String JDBC = "jdbc:postgresql://localhost:55432/agentic?user=agentic&password=agentic";
    private static E2eDriver driver;

    @BeforeAll
    static void start() throws Exception {
        driver = new E2eDriver();
    }

    @AfterAll
    static void stop() {
        driver.close();
    }

    @BeforeEach
    void reset() {
        driver.reset();
        new JdbcExecutor(JDBC).execute("DELETE FROM lessons");
    }

    @Test
    void seededLessonIsInjectedIntoTheIssue() {
        new LessonRepository(new JdbcExecutor(JDBC), new FakeLlm()).upsert(new LessonDraft("acme/payments", List.of("src/main/java"), null, "java",
                List.of(), "review_feedback", "Applying voucher discounts", "Use DiscountPolicy.cap() instead of inline math", "seed", "repo"));
        driver.jira("E2E-9", "approved");
        driver.awaitState("E2E-9", RunState.CODING, T);
        assertThat(driver.wiremockCount("POST", "/repos/acme/payments/issues", "Use DiscountPolicy.cap() instead of inline math")).isEqualTo(1);
    }

    @Test
    void mcpEndpointListsToolsAndSearches() throws Exception {
        new LessonRepository(new JdbcExecutor(JDBC), new FakeLlm()).upsert(new LessonDraft("acme/payments", List.of(), null, null,
                List.of(), "review_feedback", "Money formatting", "Use BigDecimal for currency", "seed", "repo"));
        String list = mcp("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}");
        assertThat(list).contains("search_memory");
        String found = mcp("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\",\"params\":{\"name\":\"search_memory\",\"arguments\":{\"query\":\"currency money\"}}}");
        assertThat(found).contains("Use BigDecimal for currency");
    }

    private String mcp(String body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(driver.apiUrl("/mcp")))
                .header("Authorization", "Bearer local-mcp-token")
                .header("X-Agentic-Repo", "acme/payments")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        HttpResponse<String> res = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
        assertThat(res.statusCode()).isEqualTo(200);
        return res.body();
    }
}
