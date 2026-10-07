package io.agentic.functions.learning;

import io.agentic.memory.LessonRecord;

import java.time.Duration;
import java.time.Instant;

public final class PromotionPolicy {
    public static final int MIN_HITS = 3;
    public static final double MIN_HELPED_RATIO = 0.6;
    public static final Duration UNUSED = Duration.ofDays(90);
    public static final String REJECTED_TAG = "promotion-rejected";

    private PromotionPolicy() {
    }

    public static boolean isCandidate(LessonRecord l) {
        return "active".equals(l.status())
                && l.repo() != null
                && !l.tags().contains(REJECTED_TAG)
                && l.hits() >= MIN_HITS
                && l.helped() / (double) l.hits() >= MIN_HELPED_RATIO;
    }

    public static boolean isExpired(LessonRecord l, Instant now) {
        if (!"active".equals(l.status())) {
            return false;
        }
        Instant lastSeen = l.lastUsedAt() != null ? l.lastUsedAt() : l.createdAt();
        boolean unused = lastSeen != null && lastSeen.isBefore(now.minus(UNUSED));
        boolean mostlyIgnored = l.hits() >= MIN_HITS && l.ignored() > l.helped();
        return unused || mostlyIgnored;
    }
}
