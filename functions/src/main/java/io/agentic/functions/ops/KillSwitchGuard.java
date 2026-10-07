package io.agentic.functions.ops;

import io.agentic.functions.config.Params;

import java.util.Optional;

public class KillSwitchGuard {
    private final Params params;

    public KillSwitchGuard(Params params) {
        this.params = params;
    }

    public Optional<String> check(String repo) {
        if (!params.isGloballyEnabled()) {
            return Optional.of("Paused by kill switch (global)");
        }
        if (!params.isEnabled(repo)) {
            return Optional.of("Paused by kill switch (" + repo + ")");
        }
        return Optional.empty();
    }
}
