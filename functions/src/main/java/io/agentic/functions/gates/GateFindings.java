package io.agentic.functions.gates;

import java.util.List;

public record GateFindings(String headSha, List<GateFinding> blocking, List<GateFinding> advisory, List<String> scopeViolations) {
    public GateFindings {
        blocking = blocking == null ? List.of() : List.copyOf(blocking);
        advisory = advisory == null ? List.of() : List.copyOf(advisory);
        scopeViolations = scopeViolations == null ? List.of() : List.copyOf(scopeViolations);
    }

    public static GateFindings empty(String headSha) {
        return new GateFindings(headSha, List.of(), List.of(), List.of());
    }

    public GateFindings withScopeViolations(List<String> violations) {
        return new GateFindings(headSha, blocking, advisory, violations);
    }
}
