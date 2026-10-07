package io.agentic.memory;

import java.time.Instant;
import java.util.List;

public record LessonRecord(String id, String repo, List<String> paths, String component, String language, List<String> tags,
                           String kind, String trigger, String lesson, List<String> evidence, int hits, int helped, int ignored,
                           Instant createdAt, Instant lastUsedAt, String status, String scope) {
}
