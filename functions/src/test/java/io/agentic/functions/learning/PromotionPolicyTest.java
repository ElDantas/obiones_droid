package io.agentic.functions.learning;

import io.agentic.memory.LessonRecord;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PromotionPolicyTest {
    private static final Instant NOW = Instant.parse("2026-10-07T12:00:00Z");

    static LessonRecord lesson(int hits, int helped, int ignored, Instant lastUsed, List<String> tags) {
        return new LessonRecord("11111111-1111-1111-1111-111111111111", "acme/payments", List.of("src/main"), "orders", "java", tags,
                "review_feedback", "t", "l", List.of("e"), hits, helped, ignored, NOW.minus(Duration.ofDays(10)), lastUsed, "active", "repo");
    }

    @Test
    void candidateThresholds() {
        assertThat(PromotionPolicy.isCandidate(lesson(3, 2, 0, NOW, List.of()))).isTrue();
        assertThat(PromotionPolicy.isCandidate(lesson(3, 1, 0, NOW, List.of()))).isFalse();
        assertThat(PromotionPolicy.isCandidate(lesson(2, 2, 0, NOW, List.of()))).isFalse();
        assertThat(PromotionPolicy.isCandidate(lesson(5, 5, 0, NOW, List.of(PromotionPolicy.REJECTED_TAG)))).isFalse();
    }

    @Test
    void expiryRules() {
        assertThat(PromotionPolicy.isExpired(lesson(1, 0, 0, NOW.minus(Duration.ofDays(91)), List.of()), NOW)).isTrue();
        assertThat(PromotionPolicy.isExpired(lesson(4, 1, 3, NOW, List.of()), NOW)).isTrue();
        assertThat(PromotionPolicy.isExpired(lesson(4, 3, 1, NOW, List.of()), NOW)).isFalse();
    }
}
