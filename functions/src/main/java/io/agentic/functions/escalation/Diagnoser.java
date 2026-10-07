package io.agentic.functions.escalation;

import io.agentic.core.text.Scrubber;
import io.agentic.functions.readiness.Prompts;
import io.agentic.integrations.llm.Prompt;
import io.agentic.integrations.llm.TextModel;

import java.util.List;
import java.util.function.Supplier;

public class Diagnoser {
    public static final String UNAVAILABLE = "Diagnosis unavailable.";

    private final TextModel model;
    private final Supplier<String> modelId;
    private final Prompt prompt;

    public Diagnoser(TextModel model, Supplier<String> modelId) {
        this.model = model;
        this.modelId = modelId;
        this.prompt = Prompts.load("diagnosis");
    }

    public String diagnose(String reason, List<String> findingMessages, List<String> commitMessages, String acceptanceCriteria) {
        String user = "Escalation reason: " + reason
                + "\n\nFailing findings:\n- " + String.join("\n- ", findingMessages.isEmpty() ? List.of("none") : findingMessages)
                + "\n\nLatest commits:\n- " + String.join("\n- ", commitMessages.isEmpty() ? List.of("none") : commitMessages)
                + "\n\nAcceptance criteria:\n" + (acceptanceCriteria == null ? "" : acceptanceCriteria);
        try {
            String text = model.converse(modelId.get(), prompt, Scrubber.scrub(user)).strip();
            return text.isEmpty() ? UNAVAILABLE : Scrubber.scrub(text);
        } catch (RuntimeException e) {
            System.err.println("WARN diagnosis failed: " + e.getMessage());
            return UNAVAILABLE;
        }
    }
}
