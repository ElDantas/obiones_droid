package io.agentic.memory;

import java.util.List;

public record LessonDraft(String repo, List<String> paths, String component, String language, List<String> tags,
                          String kind, String trigger, String lesson, String evidence, String scope) {
}
