package io.agentic.functions.memory;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import io.agentic.functions.config.Services;
import io.agentic.functions.ingress.HmacVerifier;
import io.agentic.functions.ingress.Responses;
import io.agentic.integrations.http.Json;
import io.agentic.memory.HybridSearch;
import io.agentic.memory.mcp.McpRouter;
import io.agentic.memory.mcp.McpTools;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

public class McpHandler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {
    private final Supplier<String> bearerToken;
    private final Supplier<Set<String>> allowlist;
    private final Supplier<McpRouter> router;

    public McpHandler() {
        this(() -> Services.instance().secrets().getJson("agentic/mcp").path("bearerToken").asText(),
                McpHandler::allowlistFromParams,
                () -> new McpRouter(new McpTools(Services.instance().hybridSearch(), Services.instance().lessons())));
    }

    McpHandler(Supplier<String> bearerToken, Supplier<Set<String>> allowlist, Supplier<McpRouter> router) {
        this.bearerToken = bearerToken;
        this.allowlist = allowlist;
        this.router = router;
    }

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent event, Context context) {
        String auth = Responses.header(event, "authorization");
        String provided = auth != null && auth.startsWith("Bearer ") ? auth.substring(7) : null;
        if (!HmacVerifier.validToken(bearerToken.get(), provided)) {
            return Responses.json(401, "{\"error\":\"unauthorized\"}");
        }
        String repo = Optional.ofNullable(Responses.header(event, "x-agentic-repo")).map(r -> r.trim().toLowerCase(Locale.ROOT)).orElse("");
        if (!allowlist.get().contains(repo)) {
            return Responses.json(403, "{\"error\":\"repository not allowed\"}");
        }
        Optional<String> response = router.get().handle(Responses.body(event), repo);
        return response.map(r -> Responses.json(200, r))
                .orElseGet(() -> APIGatewayV2HTTPResponse.builder().withStatusCode(202).withBody("").build());
    }

    private static Set<String> allowlistFromParams() {
        try {
            return Set.copyOf(Json.MAPPER.readValue(Services.instance().params().find("/agentic/repos/allowlist").orElse("[]"),
                    new TypeReference<List<String>>() {
                    }));
        } catch (Exception e) {
            return Set.of();
        }
    }
}
