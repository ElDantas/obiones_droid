package io.agentic.integrations.github.model;

import java.util.List;

public record Commit(String sha, String authorLogin, String message, List<String> files) {
}
