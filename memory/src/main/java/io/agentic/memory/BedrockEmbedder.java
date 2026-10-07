package io.agentic.memory;

import com.fasterxml.jackson.databind.JsonNode;
import io.agentic.integrations.http.Json;
import io.agentic.integrations.llm.Embeddings;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;

import java.util.Map;
import java.util.function.Supplier;

public class BedrockEmbedder implements Embeddings {
    private final BedrockRuntimeClient client;
    private final Supplier<String> modelId;

    public BedrockEmbedder(BedrockRuntimeClient client, Supplier<String> modelId) {
        this.client = client;
        this.modelId = modelId;
    }

    @Override
    public float[] embed(String text) {
        try {
            String body = Json.MAPPER.writeValueAsString(Map.of("inputText", text, "dimensions", DIMENSIONS, "normalize", true));
            JsonNode res = Json.MAPPER.readTree(client.invokeModel(InvokeModelRequest.builder()
                    .modelId(modelId.get())
                    .contentType("application/json")
                    .accept("application/json")
                    .body(SdkBytes.fromUtf8String(body))
                    .build()).body().asUtf8String());
            JsonNode values = res.path("embedding");
            float[] v = new float[values.size()];
            for (int i = 0; i < v.length; i++) {
                v[i] = (float) values.get(i).asDouble();
            }
            return v;
        } catch (Exception e) {
            throw new IllegalStateException("Embedding failed", e);
        }
    }
}
