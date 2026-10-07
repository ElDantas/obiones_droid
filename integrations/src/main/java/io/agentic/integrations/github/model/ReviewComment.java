package io.agentic.integrations.github.model;

public record ReviewComment(long id, String authorLogin, String path, Integer line, String body, String commitId, Long reviewId) {
}
