package io.agentic.functions.ingress;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.databind.JsonNode;
import io.agentic.functions.config.Services;
import io.agentic.functions.config.Wiring;
import io.agentic.functions.metrics.Metrics;
import io.agentic.functions.run.SignalDispatcher;
import io.agentic.functions.store.RunStore;
import io.agentic.integrations.http.Json;

import java.time.Clock;
import java.util.List;
import java.util.function.Supplier;

public class GitHubWebhookHandler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {
    private final Supplier<String> webhookSecret;
    private final Supplier<RunStore> runStore;
    private final Supplier<GitHubEventRouter> router;
    private final Supplier<SignalDispatcher> dispatcher;
    private final Clock clock;

    public GitHubWebhookHandler() {
        this(() -> Services.instance().secrets().getJson("agentic/github-app").path("webhookSecret").asText(),
                () -> Services.instance().runStore(),
                Wiring::router,
                Wiring::dispatcher,
                Clock.systemUTC());
    }

    GitHubWebhookHandler(Supplier<String> webhookSecret, Supplier<RunStore> runStore, Supplier<GitHubEventRouter> router,
                         Supplier<SignalDispatcher> dispatcher, Clock clock) {
        this.webhookSecret = webhookSecret;
        this.runStore = runStore;
        this.router = router;
        this.dispatcher = dispatcher;
        this.clock = clock;
    }

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent event, Context context) {
        String body = Responses.body(event);
        if (!HmacVerifier.validGitHub(webhookSecret.get(), body, Responses.header(event, "x-hub-signature-256"))) {
            return Responses.json(401, "{\"error\":\"bad signature\"}");
        }
        String delivery = Responses.header(event, "x-github-delivery");
        if (delivery != null && !runStore.get().markDelivery(delivery, clock.instant())) {
            return Responses.json(200, "{\"result\":\"duplicate\"}");
        }
        String eventName = Responses.header(event, "x-github-event");
        try {
            JsonNode payload = Json.MAPPER.readTree(body);
            List<RoutedSignal> signals = router.get().route(eventName == null ? "" : eventName, payload);
            int delivered = 0;
            for (RoutedSignal s : signals) {
                if (dispatcher.get().dispatch(s)) {
                    delivered++;
                }
            }
            return Responses.json(200, "{\"result\":\"ok\",\"signals\":" + signals.size() + ",\"delivered\":" + delivered + "}");
        } catch (Exception e) {
            e.printStackTrace();
            Metrics.count("SignalDispatchError", "github");
            return Responses.json(200, "{\"result\":\"error\"}");
        }
    }
}
