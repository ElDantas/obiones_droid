package io.agentic.integrations.github;

public class CopilotUnavailableException extends RuntimeException {
    public CopilotUnavailableException(String repo) {
        super("Copilot coding agent is not assignable in " + repo + "; check that it is enabled for the repository and the service user has a Copilot seat");
    }
}
