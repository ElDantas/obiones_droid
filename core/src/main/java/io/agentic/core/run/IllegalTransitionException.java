package io.agentic.core.run;

public class IllegalTransitionException extends RuntimeException {
    public IllegalTransitionException(RunState from, RunState to) {
        super("Illegal transition " + from + " -> " + to);
    }
}
