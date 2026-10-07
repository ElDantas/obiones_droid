package io.agentic.integrations.github.model;

public record Review(long id, String authorLogin, String state, String body, String commitId) {
}
