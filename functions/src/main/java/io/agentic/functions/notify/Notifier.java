package io.agentic.functions.notify;

import io.agentic.core.run.RunState;
import io.agentic.functions.store.Run;

public interface Notifier {
    void onTransition(Run run, RunState from, RunState to, String reason);
}
