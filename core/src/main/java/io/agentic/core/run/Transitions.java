package io.agentic.core.run;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static io.agentic.core.run.RunState.ABORTED;
import static io.agentic.core.run.RunState.CODING;
import static io.agentic.core.run.RunState.CONTEXT;
import static io.agentic.core.run.RunState.DONE;
import static io.agentic.core.run.RunState.ESCALATED;
import static io.agentic.core.run.RunState.FIXING;
import static io.agentic.core.run.RunState.GATES;
import static io.agentic.core.run.RunState.HUMAN_FIX;
import static io.agentic.core.run.RunState.HUMAN_REVIEW;
import static io.agentic.core.run.RunState.NEEDS_INFO;
import static io.agentic.core.run.RunState.READINESS;

public final class Transitions {
    private static final Map<RunState, Set<RunState>> ALLOWED = new EnumMap<>(RunState.class);

    static {
        ALLOWED.put(READINESS, EnumSet.of(CONTEXT, NEEDS_INFO, ESCALATED, ABORTED));
        ALLOWED.put(NEEDS_INFO, EnumSet.noneOf(RunState.class));
        ALLOWED.put(CONTEXT, EnumSet.of(CODING, ESCALATED, ABORTED));
        ALLOWED.put(CODING, EnumSet.of(GATES, HUMAN_REVIEW, ESCALATED, ABORTED));
        ALLOWED.put(GATES, EnumSet.of(HUMAN_REVIEW, FIXING, ESCALATED, ABORTED));
        ALLOWED.put(FIXING, EnumSet.of(GATES, HUMAN_REVIEW, ESCALATED, ABORTED));
        ALLOWED.put(HUMAN_REVIEW, EnumSet.of(HUMAN_FIX, DONE, ABORTED));
        ALLOWED.put(HUMAN_FIX, EnumSet.of(GATES, HUMAN_REVIEW, ESCALATED, ABORTED));
        ALLOWED.put(ESCALATED, EnumSet.of(CODING, GATES, FIXING, HUMAN_FIX, HUMAN_REVIEW, ABORTED));
        ALLOWED.put(DONE, EnumSet.noneOf(RunState.class));
        ALLOWED.put(ABORTED, EnumSet.noneOf(RunState.class));
    }

    private Transitions() {
    }

    public static boolean isAllowed(RunState from, RunState to) {
        return ALLOWED.get(from).contains(to);
    }

    public static void check(RunState from, RunState to) {
        if (!isAllowed(from, to)) {
            throw new IllegalTransitionException(from, to);
        }
    }
}
