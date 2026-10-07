package io.agentic.integrations.github.model;

public record PullRequest(int number, String state, boolean draft, boolean merged, String headSha, String headRef, String authorLogin, String htmlUrl, int additions, int deletions, int changedFiles, String nodeId) {
}
