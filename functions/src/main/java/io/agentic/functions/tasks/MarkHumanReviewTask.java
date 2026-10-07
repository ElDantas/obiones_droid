package io.agentic.functions.tasks;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import io.agentic.core.run.Actor;
import io.agentic.core.run.RunState;
import io.agentic.functions.config.Services;
import io.agentic.functions.config.Wiring;
import io.agentic.functions.notify.RunTransitions;
import io.agentic.functions.readiness.RepoConfigLoader;
import io.agentic.functions.store.Run;
import io.agentic.functions.store.RunStore;
import io.agentic.integrations.github.GitHubClient;
import io.agentic.integrations.github.model.PullRequest;

import java.util.Map;

public class MarkHumanReviewTask implements RequestHandler<Map<String, Object>, Map<String, Object>> {
    private final RunStore store;
    private final RunTransitions transitions;
    private final GitHubClient github;
    private final RepoConfigLoader configLoader;

    public MarkHumanReviewTask() {
        this(Services.instance().runStore(), Wiring.transitions(), Services.instance().github(), new RepoConfigLoader(Services.instance().github()));
    }

    MarkHumanReviewTask(RunStore store, RunTransitions transitions, GitHubClient github, RepoConfigLoader configLoader) {
        this.store = store;
        this.transitions = transitions;
        this.github = github;
        this.configLoader = configLoader;
    }

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        String key = TaskSupport.ticketKey(input);
        Run run = store.get(key).orElseThrow();
        PullRequest pr = github.getPullRequest(run.repo(), run.prNumber());
        if (pr.draft()) {
            github.markReadyForReview(run.repo(), run.prNumber());
        }
        github.requestReviewers(run.repo(), run.prNumber(), configLoader.load(run.repo(), run.budgets()).reviewers());
        transitions.moveTo(key, RunState.HUMAN_REVIEW, run.humanOverride() ? Actor.HUMAN : Actor.BOT, "Ready for human review");
        return Map.of("prNumber", run.prNumber());
    }
}
