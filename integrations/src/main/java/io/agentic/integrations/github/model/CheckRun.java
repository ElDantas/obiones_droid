package io.agentic.integrations.github.model;

public record CheckRun(long id, String name, String status, String conclusion, String outputSummary, String outputText) {
}
