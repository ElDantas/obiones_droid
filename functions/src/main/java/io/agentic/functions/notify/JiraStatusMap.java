package io.agentic.functions.notify;

import io.agentic.core.run.RunState;

import java.util.Optional;

public final class JiraStatusMap {
    public static final String BLOCKED = "Blocked (Agent)";
    public static final String IN_REVIEW = "In Review";
    public static final String IN_PROGRESS = "In Progress";
    public static final String DONE = "Done";

    private JiraStatusMap() {
    }

    public static Optional<String> statusFor(RunState from, RunState to) {
        return switch (to) {
            case NEEDS_INFO, ESCALATED -> Optional.of(BLOCKED);
            case HUMAN_REVIEW -> Optional.of(IN_REVIEW);
            case DONE -> Optional.of(DONE);
            default -> from == RunState.ESCALATED && to.isActive() ? Optional.of(IN_PROGRESS) : Optional.empty();
        };
    }
}
