package io.agentic.functions.store;

import io.agentic.core.budget.Budgets;
import io.agentic.core.budget.RunUsage;
import io.agentic.core.loop.IterationSnapshot;
import io.agentic.core.run.RunState;

import java.util.List;
import java.util.Map;

public record Run(
        String ticketKey,
        String runId,
        String repo,
        RunState state,
        Integer issueNumber,
        Integer prNumber,
        String executionArn,
        String slackThreadTs,
        Budgets budgets,
        RunUsage usage,
        List<IterationSnapshot> snapshots,
        boolean humanOverride,
        List<String> injectedLessonIds,
        Map<String, Object> escalation,
        RunState escalatedFrom) {
}
