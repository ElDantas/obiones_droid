package io.agentic.functions.escalation;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import io.agentic.functions.config.Services;
import io.agentic.functions.config.Wiring;
import io.agentic.functions.ingress.Responses;
import io.agentic.functions.ingress.RoutedSignal;
import io.agentic.functions.readiness.RepoConfigLoader;
import io.agentic.functions.run.SignalDispatcher;
import io.agentic.functions.store.Run;
import io.agentic.functions.store.RunStore;
import io.agentic.functions.store.WaitKind;
import io.agentic.integrations.http.Json;
import io.agentic.integrations.jira.JiraClient;
import io.agentic.integrations.slack.SlackClient;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public class SlackActionsHandler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {
    private final Supplier<String> signingSecret;
    private final Supplier<RunStore> store;
    private final Supplier<SlackClient> slack;
    private final Supplier<JiraClient> jira;
    private final Supplier<RepoConfigLoader> configLoader;
    private final Supplier<EscalationAuthorizer> authorizer;
    private final Supplier<SignalDispatcher> dispatcher;
    private final Clock clock;

    public SlackActionsHandler() {
        this(() -> Services.instance().secrets().getJson("agentic/slack").path("signingSecret").asText(),
                () -> Services.instance().runStore(),
                () -> Services.instance().slack(),
                () -> Services.instance().jira(),
                () -> new RepoConfigLoader(Services.instance().github()),
                () -> new EscalationAuthorizer(Services.instance().slack()),
                Wiring::dispatcher,
                Clock.systemUTC());
    }

    SlackActionsHandler(Supplier<String> signingSecret, Supplier<RunStore> store, Supplier<SlackClient> slack, Supplier<JiraClient> jira,
                        Supplier<RepoConfigLoader> configLoader, Supplier<EscalationAuthorizer> authorizer, Supplier<SignalDispatcher> dispatcher,
                        Clock clock) {
        this.signingSecret = signingSecret;
        this.store = store;
        this.slack = slack;
        this.jira = jira;
        this.configLoader = configLoader;
        this.authorizer = authorizer;
        this.dispatcher = dispatcher;
        this.clock = clock;
    }

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent event, Context context) {
        String body = Responses.body(event);
        if (!SlackSignature.valid(signingSecret.get(), Responses.header(event, "x-slack-request-timestamp"), body,
                Responses.header(event, "x-slack-signature"), clock)) {
            return Responses.json(401, "{\"error\":\"bad signature\"}");
        }
        JsonNode payload;
        JsonNode value;
        try {
            payload = Json.MAPPER.readTree(formField(body, "payload"));
            value = Json.MAPPER.readTree(payload.at("/actions/0/value").asText());
        } catch (Exception e) {
            return Responses.json(400, "{\"error\":\"invalid payload\"}");
        }
        String userId = payload.at("/user/id").asText();
        String channel = payload.at("/channel/id").asText(payload.at("/container/channel_id").asText());
        String messageTs = payload.at("/message/ts").asText(payload.at("/container/message_ts").asText());
        String ticketKey = value.path("ticketKey").asText();
        String escalationId = value.path("escalationId").asText();
        String decision = value.path("decision").asText();
        if (!EscalationCard.DECISIONS.contains(decision)) {
            return Responses.json(400, "{\"error\":\"unknown decision\"}");
        }
        Optional<Run> run = store.get().get(ticketKey);
        if (run.isEmpty()) {
            slack.get().ephemeral(channel, userId, "This run no longer exists.");
            return Responses.json(200, "");
        }
        String assignee = jira.get().getTicket(ticketKey).assigneeEmail();
        List<String> approvers = configLoader.get().load(run.get().repo(), run.get().budgets()).escalationApprovers();
        if (!authorizer.get().isAllowed(userId, assignee, approvers)) {
            slack.get().ephemeral(channel, userId, "You're not allowed to resolve this escalation.");
            return Responses.json(200, "");
        }
        if (!store.get().claimEscalation(ticketKey, escalationId, userId)) {
            String by = store.get().string(ticketKey, "escalationResolvedBy").map(u -> " by <@" + u + ">").orElse("");
            slack.get().ephemeral(channel, userId, "Already handled" + by + ".");
            return Responses.json(200, "");
        }
        dispatcher.get().dispatch(new RoutedSignal(ticketKey, WaitKind.ESCALATION_DECISION,
                "{\"decision\":\"" + decision + "\",\"by\":\"" + userId + "\"}"));
        try {
            List<Map<String, Object>> blocks = Json.MAPPER.convertValue(payload.at("/message/blocks"), new TypeReference<>() {
            });
            slack.get().update(channel, messageTs, EscalationCard.resolved(blocks == null ? List.of() : blocks, decision, userId),
                    ticketKey + ": " + decision + " by " + userId);
        } catch (RuntimeException e) {
            System.err.println("WARN card update failed for " + ticketKey + ": " + e.getMessage());
        }
        return Responses.json(200, "");
    }

    static String formField(String body, String name) {
        for (String pair : body.split("&")) {
            int i = pair.indexOf('=');
            if (i > 0 && URLDecoder.decode(pair.substring(0, i), StandardCharsets.UTF_8).equals(name)) {
                return URLDecoder.decode(pair.substring(i + 1), StandardCharsets.UTF_8);
            }
        }
        throw new IllegalArgumentException("Missing form field " + name);
    }
}
