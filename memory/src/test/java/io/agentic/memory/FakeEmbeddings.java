package io.agentic.memory;

import io.agentic.integrations.llm.Embeddings;
import io.agentic.integrations.llm.FakeLlm;

import java.util.HashMap;
import java.util.Map;

public class FakeEmbeddings implements Embeddings {
    private final Map<String, float[]> fixed = new HashMap<>();
    private final FakeLlm fallback = new FakeLlm();

    public FakeEmbeddings with(String text, float[] vector) {
        fixed.put(text, vector);
        return this;
    }

    @Override
    public float[] embed(String text) {
        return fixed.getOrDefault(text, fallback.embed(text));
    }

    public static float[] axis(int index, double weight, int other, double otherWeight) {
        float[] v = new float[DIMENSIONS];
        v[index] = (float) weight;
        v[other] = (float) otherWeight;
        double norm = Math.sqrt(weight * weight + otherWeight * otherWeight);
        for (int i = 0; i < v.length; i++) {
            v[i] /= (float) norm;
        }
        return v;
    }
}
