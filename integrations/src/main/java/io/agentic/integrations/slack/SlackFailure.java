package io.agentic.integrations.slack;

public class SlackFailure extends RuntimeException {
    public SlackFailure(String method, String error) {
        super("Slack " + method + " failed: " + error);
    }
}
