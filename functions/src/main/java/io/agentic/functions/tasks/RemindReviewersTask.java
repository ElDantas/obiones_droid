package io.agentic.functions.tasks;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import io.agentic.functions.config.Services;
import io.agentic.functions.config.Wiring;
import io.agentic.functions.notify.Links;
import io.agentic.functions.notify.SlackNotifier;
import io.agentic.functions.readiness.RepoConfigLoader;
import io.agentic.functions.store.Run;
import io.agentic.functions.store.RunStore;

import java.util.List;
import java.util.Map;

public class RemindReviewersTask implements RequestHandler<Map<String, Object>, Map<String, Object>> {
    private final RunStore store;
    private final SlackNotifier slack;
    private final RepoConfigLoader configLoader;
    private final Links links;

    public RemindReviewersTask() {
        this(Services.instance().runStore(), Wiring.slackNotifier(), new RepoConfigLoader(Services.instance().github()), Wiring.links());
    }

    RemindReviewersTask(RunStore store, SlackNotifier slack, RepoConfigLoader configLoader, Links links) {
        this.store = store;
        this.slack = slack;
        this.configLoader = configLoader;
        this.links = links;
    }

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        String key = TaskSupport.ticketKey(input);
        Run run = store.get(key).orElseThrow();
        List<String> reviewers = configLoader.load(run.repo(), run.budgets()).reviewers();
        String who = reviewers.isEmpty() ? "" : " Reviewers: " + String.join(", ", reviewers);
        slack.post(run, "⏰ Still waiting for review on " + links.pr(run.repo(), run.prNumber()) + " (3 days)." + who);
        return Map.of("reminded", true);
    }
}
