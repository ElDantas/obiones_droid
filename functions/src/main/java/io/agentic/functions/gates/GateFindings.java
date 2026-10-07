package io.agentic.functions.gates;

import java.util.List;
import java.util.Map;

public record GateFindings(String headSha, List<GateFinding> blocking, List<GateFinding> advisory, List<String> scopeViolations,
                           Map<String, Integer> agentPremiumRequests) {
    public GateFindings {
        blocking = blocking == null ? List.of() : List.copyOf(blocking);
        advisory = advisory == null ? List.of() : List.copyOf(advisory);
        scopeViolations = scopeViolations == null ? List.of() : List.copyOf(scopeViolations);
        agentPremiumRequests = agentPremiumRequests == null ? Map.of() : Map.copyOf(agentPremiumRequests);
    }

    public GateFindings(String headSha, List<GateFinding> blocking, List<GateFinding> advisory, List<String> scopeViolations) {
        this(headSha, blocking, advisory, scopeViolations, Map.of());
    }

    public static GateFindings empty(String headSha) {
        return new GateFindings(headSha, List.of(), List.of(), List.of(), Map.of());
    }

    public GateFindings withScopeViolations(List<String> violations) {
        return new GateFindings(headSha, blocking, advisory, violations, agentPremiumRequests);
    }
}
