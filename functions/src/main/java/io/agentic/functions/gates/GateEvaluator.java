package io.agentic.functions.gates;

import io.agentic.functions.tasks.TaskSupport;

import java.util.Map;

public final class GateEvaluator {

    public Map<String, Object> decide(GateFindings f, boolean humanOverride, boolean prPresent) {
        if (!prPresent) {
            return TaskSupport.decision("ESCALATE", "No PR linked to the issue");
        }
        if (humanOverride) {
            return TaskSupport.decision("HUMAN_OVERRIDE", "A human pushed to the PR branch");
        }
        if (!f.scopeViolations().isEmpty()) {
            return TaskSupport.decision("ESCALATE", String.join("; ", f.scopeViolations()));
        }
        if (!f.blocking().isEmpty()) {
            return TaskSupport.decision("FAIL", f.blocking().size() + " blocking finding(s)");
        }
        return TaskSupport.decision("PASS", "All gates passed");
    }
}
