package io.agentic.functions.memory;

import io.agentic.functions.context.Lesson;
import io.agentic.functions.support.Tickets;
import io.agentic.memory.HybridSearch;
import io.agentic.memory.LessonRecord;
import io.agentic.memory.LessonRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemoryLessonProviderTest {
    private static final Instant NOW = Instant.parse("2026-10-07T12:00:00Z");

    private static LessonRecord rec(int i) {
        return new LessonRecord("id" + i, "acme/payments", List.of(), null, null, List.of(), "review_feedback", "t" + i, "l" + i,
                List.of(), 1, 0, 0, NOW, null, "active", "repo");
    }

    @Test
    void returnsAtMostEightAndMarksThemUsed() {
        HybridSearch search = mock(HybridSearch.class);
        LessonRepository repo = mock(LessonRepository.class);
        List<LessonRecord> many = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            many.add(rec(i));
        }
        when(search.search(any())).thenReturn(many);
        List<Lesson> lessons = new MemoryLessonProvider(search, repo, Clock.fixed(NOW, ZoneOffset.UTC)).lessonsFor(Tickets.ready(), "acme/payments");
        assertThat(lessons).hasSize(8);
        verify(repo).markUsed(argThat(ids -> ids.size() == 8), eq(NOW));
    }

    @Test
    void retrievalFailureReturnsNoLessons() {
        HybridSearch search = mock(HybridSearch.class);
        when(search.search(any())).thenThrow(new IllegalStateException("db down"));
        assertThat(new MemoryLessonProvider(search, mock(LessonRepository.class), Clock.systemUTC()).lessonsFor(Tickets.ready(), "acme/payments")).isEmpty();
    }
}
