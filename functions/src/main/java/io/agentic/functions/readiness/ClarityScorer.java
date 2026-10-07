package io.agentic.functions.readiness;

import io.agentic.integrations.jira.JiraTicket;
import io.agentic.integrations.llm.Prompt;
import io.agentic.integrations.llm.TextModel;

import java.util.List;
import java.util.function.Supplier;

public class ClarityScorer {
    public record Clarity(int score, List<String> gaps) {
        public Clarity {
            gaps = gaps == null ? List.of() : gaps;
        }
    }

    private final TextModel model;
    private final Supplier<String> modelId;
    private final Prompt prompt;

    public ClarityScorer(TextModel model, Supplier<String> modelId) {
        this.model = model;
        this.modelId = modelId;
        this.prompt = Prompts.load("clarity");
    }

    public Clarity score(JiraTicket t) {
        String user = "Summary: " + t.summary() + "\n\nDescription:\n" + t.description() + "\n\nAcceptance criteria:\n" + t.acceptanceCriteria();
        try {
            Clarity c = model.converseJson(modelId.get(), prompt, user, Clarity.class);
            return new Clarity(c.score(), c.gaps().stream().limit(5).toList());
        } catch (RuntimeException e) {
            System.err.println("WARN clarity scoring failed for " + t.key() + ": " + e.getMessage());
            return new Clarity(100, List.of());
        }
    }
}
