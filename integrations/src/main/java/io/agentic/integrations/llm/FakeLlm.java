package io.agentic.integrations.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.agentic.integrations.http.Json;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;

public class FakeLlm implements TextModel, Embeddings {

    @Override
    public String converse(String modelId, Prompt prompt, String user) {
        return switch (prompt.name()) {
            case "clarity" -> user.contains("VAGUE")
                    ? "{\"score\":40,\"gaps\":[\"Clarify the expected result\"]}"
                    : "{\"score\":90,\"gaps\":[]}";
            case "lesson-extract" -> lesson(user);
            case "diagnosis" -> "The same check keeps failing after each fix. The acceptance criteria may conflict with an existing test.";
            default -> "{}";
        };
    }

    @Override
    public <T> T converseJson(String modelId, Prompt prompt, String user, Class<T> type) {
        try {
            return Json.MAPPER.readValue(converse(modelId, prompt, user), type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public float[] embed(String text) {
        Random random = new Random(seed(text));
        float[] v = new float[DIMENSIONS];
        double norm = 0;
        for (int i = 0; i < v.length; i++) {
            v[i] = (float) random.nextGaussian();
            norm += v[i] * v[i];
        }
        float scale = (float) (1.0 / Math.sqrt(norm));
        for (int i = 0; i < v.length; i++) {
            v[i] *= scale;
        }
        return v;
    }

    private static String lesson(String user) {
        String first = user.strip().split("[.\\n]", 2)[0].strip();
        if (first.toLowerCase().startsWith("nit")) {
            return "{\"generalisable\":false}";
        }
        try {
            return Json.MAPPER.writeValueAsString(java.util.Map.of(
                    "generalisable", true,
                    "trigger", "When changing similar code",
                    "lesson", first,
                    "tags", java.util.List.of("fake")));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static long seed(String text) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            long s = 0;
            for (int i = 0; i < 8; i++) {
                s = (s << 8) | (h[i] & 0xff);
            }
            return s;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
