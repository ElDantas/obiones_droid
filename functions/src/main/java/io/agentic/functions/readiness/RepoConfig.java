package io.agentic.functions.readiness;

import io.agentic.core.budget.Budgets;

import java.util.List;

public record RepoConfig(Budgets budgets, List<String> reviewers, List<String> escalationApprovers, List<String> requiredChecks) {
}
