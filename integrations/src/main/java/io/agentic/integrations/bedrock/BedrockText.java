package io.agentic.integrations.bedrock;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.agentic.integrations.http.Json;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BedrockText {
    private static final Pattern FENCED = Pattern.compile("(?s)^\\s*```[a-zA-Z]*\\s*(.*?)\\s*```\\s*$");

    private final BedrockRuntimeClient client;

    public BedrockText(BedrockRuntimeClient client) {
        this.client = client;
    }

    public String converse(String modelId, String system, String user) {
        ConverseResponse res = client.converse(ConverseRequest.builder()
                .modelId(modelId)
                .system(SystemContentBlock.fromText(system))
                .messages(Message.builder().role(ConversationRole.USER).content(ContentBlock.fromText(user)).build())
                .inferenceConfig(InferenceConfiguration.builder().temperature(0f).maxTokens(1024).build())
                .build());
        StringBuilder sb = new StringBuilder();
        for (ContentBlock b : res.output().message().content()) {
            if (b.text() != null) {
                sb.append(b.text());
            }
        }
        return sb.toString();
    }

    public <T> T converseJson(String modelId, String system, String user, Class<T> type) {
        String text = converse(modelId, system + "\nRespond with only a JSON object.", user);
        try {
            return Json.MAPPER.readValue(stripFence(text), type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Model returned invalid JSON: " + text, e);
        }
    }

    static String stripFence(String text) {
        Matcher m = FENCED.matcher(text);
        return m.matches() ? m.group(1) : text.strip();
    }
}
