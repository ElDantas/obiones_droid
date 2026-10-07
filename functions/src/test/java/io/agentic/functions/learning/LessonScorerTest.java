package io.agentic.functions.learning;

import io.agentic.integrations.github.model.ReviewComment;
import io.agentic.integrations.llm.Embeddings;
import io.agentic.memory.LessonRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LessonScorerTest {
    private final LessonRepository repo = mock(LessonRepository.class);

    private static float[] v(float... values) {
        float[] out = new float[Embeddings.DIMENSIONS];
        System.arraycopy(values, 0, out, 0, values.length);
        return out;
    }

    @Test
    void reflaggedLessonIsIgnoredOthersHelped() {
        Embeddings emb = text -> text.startsWith("t\n") ? v(1, 0) : text.equals("same problem again") ? v(0.95f, 0.05f) : v(0, 1);
        when(repo.get("a")).thenReturn(Optional.of(PromotionPolicyTest.lesson(3, 0, 0, Instant.now(), List.of())));
        new LessonScorer(repo, emb).score(List.of("a"), List.of(new ReviewComment(1, "alice", "f", 1, "same problem again", "s", 1L)));
        verify(repo).scoreIgnored("a");
        verify(repo, never()).scoreHelped("a");
    }

    @Test
    void noSimilarCommentMeansHelped() {
        Embeddings emb = text -> text.startsWith("t\n") ? v(1, 0) : v(0, 1);
        when(repo.get("a")).thenReturn(Optional.of(PromotionPolicyTest.lesson(3, 0, 0, Instant.now(), List.of())));
        new LessonScorer(repo, emb).score(List.of("a"), List.of(new ReviewComment(1, "alice", "f", 1, "unrelated", "s", 1L)));
        verify(repo).scoreHelped("a");
    }
}
