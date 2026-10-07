package io.agentic.local;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class ApiEvents {
    private ApiEvents() {
    }

    static APIGatewayV2HTTPEvent toEvent(HttpExchange exchange) throws IOException {
        Map<String, String> headers = new HashMap<>();
        for (Map.Entry<String, List<String>> e : exchange.getRequestHeaders().entrySet()) {
            if (!e.getValue().isEmpty()) {
                headers.put(e.getKey().toLowerCase(), e.getValue().get(0));
            }
        }
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        return APIGatewayV2HTTPEvent.builder()
                .withRawPath(path)
                .withRawQueryString(exchange.getRequestURI().getRawQuery())
                .withRouteKey(method + " " + path)
                .withHeaders(headers)
                .withBody(body)
                .withIsBase64Encoded(false)
                .withRequestContext(APIGatewayV2HTTPEvent.RequestContext.builder()
                        .withHttp(APIGatewayV2HTTPEvent.RequestContext.Http.builder().withMethod(method).withPath(path).build())
                        .build())
                .build();
    }

    static void write(HttpExchange exchange, APIGatewayV2HTTPResponse response) throws IOException {
        if (response.getHeaders() != null) {
            response.getHeaders().forEach((k, v) -> exchange.getResponseHeaders().add(k, v));
        }
        byte[] bytes = response.getBody() == null ? new byte[0] : response.getBody().getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(response.getStatusCode(), bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        }
        exchange.close();
    }

    static void write(HttpExchange exchange, int status, String json) throws IOException {
        write(exchange, APIGatewayV2HTTPResponse.builder()
                .withStatusCode(status)
                .withHeaders(Map.of("Content-Type", "application/json"))
                .withBody(json)
                .build());
    }
}
