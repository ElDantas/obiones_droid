package io.agentic.functions.learning;

import io.agentic.integrations.github.model.ReviewComment;
import io.agentic.integrations.llm.Embeddings;
import io.agentic.memory.LessonRecord;
import io.agentic.memory.LessonRepository;

import java.util.List;

public class LessonScorer {
    public static final double REFLAGGED_SIMILARITY = 0.85;

    private final LessonRepository repository;
    private final Embeddings embeddings;

    public LessonScorer(LessonRepository repository, Embeddings embeddings) {
        this.repository = repository;
        this.embeddings = embeddings;
    }

    public void score(List<String> injectedLessonIds, List<ReviewComment> humanComments) {
        List<float[]> commentVectors = humanComments.stream().map(c -> embeddings.embed(c.body())).toList();
        for (String id : injectedLessonIds) {
            repository.get(id).ifPresent(lesson -> {
                if (reflagged(lesson, commentVectors)) {
                    repository.scoreIgnored(id);
                } else {
                    repository.scoreHelped(id);
                }
            });
        }
    }

    private boolean reflagged(LessonRecord lesson, List<float[]> comments) {
        if (comments.isEmpty()) {
            return false;
        }
        float[] l = embeddings.embed(lesson.trigger() + "\n" + lesson.lesson());
        return comments.stream().anyMatch(c -> cosine(l, c) >= REFLAGGED_SIMILARITY);
    }

    static double cosine(float[] a, float[] b) {
        double dot = 0;
        double na = 0;
        double nb = 0;
        for (int i = 0; i < Math.min(a.length, b.length); i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        return na == 0 || nb == 0 ? 0 : dot / (Math.sqrt(na) * Math.sqrt(nb));
    }
}
