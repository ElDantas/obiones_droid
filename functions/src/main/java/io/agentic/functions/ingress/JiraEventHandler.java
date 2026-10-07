package io.agentic.functions.ingress;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.databind.JsonNode;
import io.agentic.core.run.Actor;
import io.agentic.functions.config.Services;
import io.agentic.functions.config.Wiring;
import io.agentic.functions.run.AbortService;
import io.agentic.functions.run.RunStarter;
import io.agentic.integrations.http.Json;
import io.agentic.integrations.slack.SlackClient;

import java.util.function.Supplier;
import java.util.regex.Pattern;

public class JiraEventHandler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {
    private static final Pattern KEY = Pattern.compile("^[A-Z][A-Z0-9_]+-\\d+$");

    private final Supplier<String> webhookToken;
    private final Supplier<RunStarter> starter;
    private final Supplier<AbortService> abortService;
    private final Supplier<SlackClient> slack;
    private final Supplier<String> opsChannel;

    public JiraEventHandler() {
        this(() -> Services.instance().secrets().getJson("agentic/jira").path("webhookToken").asText(),
                Wiring::runStarter,
                Wiring::abortService,
                () -> Services.instance().slack(),
                () -> Services.instance().params().find("/agentic/slack/channels/ops").orElse("#agentic-ops"));
    }

    JiraEventHandler(Supplier<String> webhookToken, Supplier<RunStarter> starter, Supplier<AbortService> abortService,
                     Supplier<SlackClient> slack, Supplier<String> opsChannel) {
        this.webhookToken = webhookToken;
        this.starter = starter;
        this.abortService = abortService;
        this.slack = slack;
        this.opsChannel = opsChannel;
    }

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent event, Context context) {
        if (!HmacVerifier.validToken(webhookToken.get(), Responses.header(event, "x-agentic-token"))) {
            return Responses.json(401, "{\"error\":\"unauthorized\"}");
        }
        JsonNode body;
        try {
            body = Json.MAPPER.readTree(Responses.body(event));
        } catch (Exception e) {
            return Responses.json(400, "{\"error\":\"invalid json\"}");
        }
        String ticketKey = body.path("ticketKey").asText("");
        String type = body.path("event").asText("");
        if (!KEY.matcher(ticketKey).matches()) {
            return Responses.json(400, "{\"error\":\"invalid ticketKey\"}");
        }
        String result = switch (type) {
            case "approved" -> start(ticketKey);
            case "unflagged" -> abortService.get().abort(ticketKey, Actor.HUMAN, "Label removed or ticket moved back") ? "ABORTED" : "NOOP";
            case "restart" -> {
                abortService.get().abort(ticketKey, Actor.HUMAN, "Restart requested from Jira");
                yield start(ticketKey);
            }
            default -> null;
        };
        if (result == null) {
            return Responses.json(400, "{\"error\":\"unknown event\"}");
        }
        return Responses.json(202, "{\"result\":\"" + result + "\"}");
    }

    private String start(String ticketKey) {
        RunStarter.StartResult r = starter.get().start(ticketKey);
        if (r == RunStarter.StartResult.DISABLED) {
            try {
                slack.get().post(opsChannel.get(), null, null, "Agentic runs are disabled; ignored approval for " + ticketKey);
            } catch (RuntimeException e) {
                System.err.println("Slack ops post failed: " + e.getMessage());
            }
        }
        return r.name();
    }
}
