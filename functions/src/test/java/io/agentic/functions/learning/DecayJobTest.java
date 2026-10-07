package io.agentic.functions.learning;

import io.agentic.memory.LessonRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DecayJobTest {

    @Test
    void expiresUnusedLessons() {
        Instant now = Instant.parse("2026-10-07T12:00:00Z");
        LessonRepository repo = mock(LessonRepository.class);
        var stale = PromotionPolicyTest.lesson(1, 0, 0, now.minus(Duration.ofDays(120)), List.of());
        when(repo.find(anyString(), eq(Map.of()))).thenReturn(List.of(stale));
        new DecayJob(repo, Clock.fixed(now, ZoneOffset.UTC)).handleRequest(Map.of(), null);
        verify(repo).setStatus(stale.id(), "expired");
    }

    @Test
    void keepsFreshLessons() {
        Instant now = Instant.parse("2026-10-07T12:00:00Z");
        LessonRepository repo = mock(LessonRepository.class);
        var fresh = PromotionPolicyTest.lesson(1, 0, 0, now.minus(Duration.ofDays(5)), List.of());
        when(repo.find(anyString(), eq(Map.of()))).thenReturn(List.of(fresh));
        new DecayJob(repo, Clock.fixed(now, ZoneOffset.UTC)).handleRequest(Map.of(), null);
        verify(repo, never()).setStatus(anyString(), anyString());
    }
}
