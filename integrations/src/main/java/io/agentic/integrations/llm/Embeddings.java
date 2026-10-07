package io.agentic.integrations.llm;

public interface Embeddings {
    int DIMENSIONS = 1024;

    float[] embed(String text);
}
