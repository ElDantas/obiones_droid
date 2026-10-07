package io.agentic.integrations.llm;

public interface TextModel {
    String converse(String modelId, Prompt prompt, String user);

    <T> T converseJson(String modelId, Prompt prompt, String user, Class<T> type);
}
