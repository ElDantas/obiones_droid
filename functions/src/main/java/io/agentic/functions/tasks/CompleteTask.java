package io.agentic.functions.tasks;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import io.agentic.core.run.Actor;
import io.agentic.core.run.RunState;
import io.agentic.functions.config.Services;
import io.agentic.functions.config.Wiring;
import io.agentic.functions.ingress.Identities;
import io.agentic.functions.learning.LessonScorer;
import io.agentic.functions.notify.RunTransitions;
import io.agentic.functions.store.Run;
import io.agentic.functions.store.RunStore;
import io.agentic.integrations.github.GitHubClient;
import io.agentic.integrations.github.model.ReviewComment;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class CompleteTask implements RequestHandler<Map<String, Object>, Map<String, Object>> {
    private final Supplier<RunTransitions> transitions;
    private final Supplier<RunStore> store;
    private final Supplier<GitHubClient> github;
    private final Supplier<Identities> identities;
    private final Supplier<LessonScorer> scorer;

    public CompleteTask() {
        this(Wiring::transitions, () -> Services.instance().runStore(), () -> Services.instance().github(), Wiring::identities,
                () -> new LessonScorer(Services.instance().lessons(), Services.instance().embeddings()));
    }

    CompleteTask(Supplier<RunTransitions> transitions, Supplier<RunStore> store, Supplier<GitHubClient> github,
                 Supplier<Identities> identities, Supplier<LessonScorer> scorer) {
        this.transitions = transitions;
        this.store = store;
        this.github = github;
        this.identities = identities;
        this.scorer = scorer;
    }

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        String key = TaskSupport.ticketKey(input);
        transitions.get().moveTo(key, RunState.DONE, Actor.HUMAN, "PR merged");
        try {
            Run run = store.get().get(key).orElseThrow();
            if (!run.injectedLessonIds().isEmpty() && run.prNumber() != null) {
                Identities ids = identities.get();
                List<ReviewComment> human = github.get().listReviewComments(run.repo(), run.prNumber()).stream()
                        .filter(c -> ids.isHuman(c.authorLogin())).toList();
                scorer.get().score(run.injectedLessonIds(), human);
            }
        } catch (RuntimeException e) {
            System.err.println("WARN lesson scoring failed for " + key + ": " + e.getMessage());
        }
        return Map.of("state", RunState.DONE.name());
    }
}
