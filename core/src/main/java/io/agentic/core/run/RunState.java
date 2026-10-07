package io.agentic.core.run;

public enum RunState {
    READINESS, NEEDS_INFO, CONTEXT, CODING, GATES, FIXING,
    HUMAN_REVIEW, HUMAN_FIX, ESCALATED, DONE, ABORTED;

    public boolean isTerminal() {
        return this == DONE || this == ABORTED || this == NEEDS_INFO;
    }

    public boolean isActive() {
        return !isTerminal();
    }
}
