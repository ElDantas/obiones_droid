package io.agentic.functions.learning;

import io.agentic.core.text.Scrubber;
import io.agentic.functions.gates.GateFinding;
import io.agentic.functions.readiness.Prompts;
import io.agentic.integrations.github.model.Commit;
import io.agentic.integrations.github.model.ReviewComment;
import io.agentic.integrations.llm.Prompt;
import io.agentic.integrations.llm.TextModel;
import io.agentic.memory.LessonDraft;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public class LessonExtractor {
    private static final Map<String, String> LANGUAGES = Map.of(
            "java", "java", "kt", "kotlin", "ts", "typescript", "tsx", "typescript", "js", "javascript",
            "py", "python", "go", "go", "rb", "ruby", "cs", "csharp", "sql", "sql");

    public record Extracted(Boolean generalisable, String trigger, String lesson, List<String> tags) {
    }

    private final TextModel model;
    private final Supplier<String> modelId;
    private final Prompt prompt;

    public LessonExtractor(TextModel model, Supplier<String> modelId) {
        this.model = model;
        this.modelId = modelId;
        this.prompt = Prompts.load("lesson-extract");
    }

    public Optional<LessonDraft> fromReviewComment(String repo, ReviewComment c, String prUrl) {
        String text = (c.path() == null ? "" : "File: " + c.path() + "\n") + c.body();
        return extract(text).map(e -> draft(repo, c.path(), e, "review_feedback", prUrl));
    }

    public Optional<LessonDraft> fromResolvedFailure(String repo, GateFinding f, List<Commit> fixCommits, String prUrl) {
        StringBuilder sb = new StringBuilder("A gate failure was fixed by the coding agent.\nFailure (" + f.gate() + "): " + f.message());
        if (f.file() != null) {
            sb.append("\nFile: ").append(f.file());
        }
        fixCommits.stream().limit(3).forEach(c -> sb.append("\nFix commit: ").append(c.message()));
        return extract(sb.toString()).map(e -> draft(repo, f.file(), e, "gate_failure", prUrl));
    }

    public Optional<LessonDraft> fromPostMortem(String repo, String reason, String diagnosis, String decision, String prUrl) {
        String text = "The coding agent got stuck and a human intervened.\nReason: " + reason + "\nDiagnosis: " + diagnosis + "\nHuman decision: " + decision;
        return extract(text).map(e -> draft(repo, null, e, "stuck_postmortem", prUrl));
    }

    private Optional<Extracted> extract(String text) {
        try {
            Extracted e = model.converseJson(modelId.get(), prompt, Scrubber.scrub(text), Extracted.class);
            if (e == null || !Boolean.TRUE.equals(e.generalisable()) || blank(e.trigger()) || blank(e.lesson())) {
                return Optional.empty();
            }
            return Optional.of(e);
        } catch (RuntimeException ex) {
            System.err.println("WARN lesson extraction failed: " + ex.getMessage());
            return Optional.empty();
        }
    }

    private static LessonDraft draft(String repo, String file, Extracted e, String kind, String evidence) {
        String dir = file == null || !file.contains("/") ? null : file.substring(0, file.lastIndexOf('/'));
        String component = file == null || !file.contains("/") ? null : file.substring(0, file.indexOf('/'));
        String language = null;
        if (file != null && file.contains(".")) {
            language = LANGUAGES.get(file.substring(file.lastIndexOf('.') + 1).toLowerCase());
        }
        return new LessonDraft(repo, dir == null ? List.of() : List.of(dir), component, language,
                e.tags() == null ? List.of() : e.tags(), kind, Scrubber.scrub(e.trigger()), Scrubber.scrub(e.lesson()), evidence, "repo");
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
