package io.agentic.functions.learning;

import io.agentic.functions.gates.GateFinding;
import io.agentic.integrations.github.model.Commit;
import io.agentic.integrations.github.model.ReviewComment;
import io.agentic.memory.LessonDraft;
import io.agentic.memory.LessonRepository;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public class LessonRecorder {
    private final LessonExtractor extractor;
    private final LessonRepository repository;

    public LessonRecorder(LessonExtractor extractor, LessonRepository repository) {
        this.extractor = extractor;
        this.repository = repository;
    }

    public int recordReviewComments(String repo, List<ReviewComment> comments, String prUrl) {
        int recorded = 0;
        for (ReviewComment c : comments) {
            recorded += safely(() -> extractor.fromReviewComment(repo, c, prUrl));
        }
        return recorded;
    }

    public int recordResolved(String repo, List<GateFinding> resolved, List<Commit> fixCommits, String prUrl) {
        int recorded = 0;
        for (GateFinding f : resolved) {
            recorded += safely(() -> extractor.fromResolvedFailure(repo, f, fixCommits, prUrl));
        }
        return recorded;
    }

    public int recordPostMortem(String repo, String reason, String diagnosis, String decision, String prUrl) {
        return safely(() -> extractor.fromPostMortem(repo, reason, diagnosis, decision, prUrl));
    }

    private int safely(Supplier<Optional<LessonDraft>> draft) {
        try {
            Optional<LessonDraft> d = draft.get();
            if (d.isEmpty()) {
                return 0;
            }
            repository.upsert(d.get());
            return 1;
        } catch (RuntimeException e) {
            System.err.println("WARN lesson recording failed: " + e.getMessage());
            return 0;
        }
    }
}
