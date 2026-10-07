package io.agentic.functions.ingress;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

public final class Responses {
    private Responses() {
    }

    public static APIGatewayV2HTTPResponse json(int status, String body) {
        return APIGatewayV2HTTPResponse.builder()
                .withStatusCode(status)
                .withHeaders(Map.of("Content-Type", "application/json"))
                .withBody(body)
                .build();
    }

    public static String header(APIGatewayV2HTTPEvent event, String name) {
        if (event.getHeaders() == null) {
            return null;
        }
        for (Map.Entry<String, String> e : event.getHeaders().entrySet()) {
            if (e.getKey().equalsIgnoreCase(name)) {
                return e.getValue();
            }
        }
        return null;
    }

    public static String body(APIGatewayV2HTTPEvent event) {
        String body = event.getBody() == null ? "" : event.getBody();
        return event.getIsBase64Encoded() ? new String(Base64.getDecoder().decode(body), StandardCharsets.UTF_8) : body;
    }
}
