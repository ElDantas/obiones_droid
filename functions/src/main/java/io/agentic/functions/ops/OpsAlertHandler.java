package io.agentic.functions.ops;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import com.fasterxml.jackson.databind.JsonNode;
import io.agentic.functions.config.Services;
import io.agentic.integrations.http.Json;
import io.agentic.integrations.slack.SlackClient;

import java.util.Map;
import java.util.function.Supplier;

public class OpsAlertHandler implements RequestHandler<SNSEvent, Map<String, Object>> {
    private final Supplier<SlackClient> slack;
    private final Supplier<String> channel;
    private final String region;

    public OpsAlertHandler() {
        this(() -> Services.instance().slack(),
                () -> Services.instance().params().find("/agentic/slack/channels/ops").orElse("#agentic-ops"),
                System.getenv().getOrDefault("AWS_REGION", "eu-west-2"));
    }

    OpsAlertHandler(Supplier<SlackClient> slack, Supplier<String> channel, String region) {
        this.slack = slack;
        this.channel = channel;
        this.region = region;
    }

    @Override
    public Map<String, Object> handleRequest(SNSEvent event, Context context) {
        int posted = 0;
        for (SNSEvent.SNSRecord r : event.getRecords()) {
            slack.get().post(channel.get(), null, null, format(r.getSNS().getMessage()));
            posted++;
        }
        return Map.of("posted", posted);
    }

    String format(String message) {
        JsonNode m;
        try {
            m = Json.MAPPER.readTree(message);
        } catch (Exception e) {
            return "🚨 " + message;
        }
        if (m.has("AlarmName")) {
            String name = m.path("AlarmName").asText();
            String icon = "ALARM".equals(m.path("NewStateValue").asText()) ? "🚨" : "✅";
            return icon + " *" + name + "* is " + m.path("NewStateValue").asText() + "\n" + m.path("NewStateReason").asText()
                    + "\nhttps://" + region + ".console.aws.amazon.com/cloudwatch/home?region=" + region + "#alarmsV2:alarm/" + name;
        }
        if ("Parameter Store Change".equals(m.path("detail-type").asText())) {
            return "🔌 Kill switch parameter *" + m.at("/detail/name").asText() + "* changed (" + m.at("/detail/operation").asText() + ")";
        }
        return "🚨 " + message;
    }
}
