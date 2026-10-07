package io.agentic.integrations.github.model;

public record Annotation(String path, Integer startLine, String title, String message) {
}
