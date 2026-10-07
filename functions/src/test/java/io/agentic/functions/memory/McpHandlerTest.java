package io.agentic.functions.memory;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import io.agentic.memory.mcp.McpRouter;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class McpHandlerTest {
    private final McpRouter router = mock(McpRouter.class);
    private final McpHandler handler = new McpHandler(() -> "tok", () -> Set.of("acme/payments"), () -> router);

    private APIGatewayV2HTTPEvent event(String auth, String repo) {
        return APIGatewayV2HTTPEvent.builder().withHeaders(Map.of("Authorization", auth, "X-Agentic-Repo", repo)).withBody("{}").build();
    }

    @Test
    void wrongBearerIs401() {
        assertThat(handler.handleRequest(event("Bearer nope", "acme/payments"), null).getStatusCode()).isEqualTo(401);
    }

    @Test
    void repoNotAllowListedIs403() {
        assertThat(handler.handleRequest(event("Bearer tok", "acme/other"), null).getStatusCode()).isEqualTo(403);
    }

    @Test
    void validRequestIsRoutedWithNormalisedRepo() {
        when(router.handle(anyString(), eq("acme/payments"))).thenReturn(Optional.of("{\"ok\":1}"));
        assertThat(handler.handleRequest(event("Bearer tok", " Acme/Payments "), null).getBody()).isEqualTo("{\"ok\":1}");
    }

    @Test
    void notificationIs202() {
        when(router.handle(anyString(), eq("acme/payments"))).thenReturn(Optional.empty());
        assertThat(handler.handleRequest(event("Bearer tok", "acme/payments"), null).getStatusCode()).isEqualTo(202);
    }
}
