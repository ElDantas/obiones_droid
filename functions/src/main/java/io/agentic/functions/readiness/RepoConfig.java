package io.agentic.functions.readiness;

import io.agentic.core.budget.Budgets;

import java.util.List;

public record RepoConfig(Budgets budgets, List<String> reviewers, List<String> escalationApprovers, List<String> requiredChecks, boolean gateAgents) {
    public RepoConfig(Budgets budgets, List<String> reviewers, List<String> escalationApprovers, List<String> requiredChecks) {
        this(budgets, reviewers, escalationApprovers, requiredChecks, false);
    }
}
