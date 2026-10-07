package io.agentic.functions.context;

import java.util.List;

public record Lesson(String id, String trigger, String lesson, List<String> evidence) {
}
