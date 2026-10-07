package io.agentic.functions.tasks;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import io.agentic.core.budget.BudgetEvaluator;
import io.agentic.core.budget.BudgetVerdict;
import io.agentic.core.run.Actor;
import io.agentic.core.run.RunState;
import io.agentic.functions.config.Services;
import io.agentic.functions.config.Wiring;
import io.agentic.functions.gates.FeedbackComposer;
import io.agentic.functions.notify.RunTransitions;
import io.agentic.functions.store.Run;
import io.agentic.functions.store.RunStore;
import io.agentic.functions.usage.UsageMeter;
import io.agentic.integrations.github.CopilotClient;
import io.agentic.integrations.github.GitHubClient;
import io.agentic.integrations.github.model.Review;
import io.agentic.integrations.github.model.ReviewComment;

import java.time.Clock;
import java.util.List;
import java.util.Map;

public class HumanFixTask implements RequestHandler<Map<String, Object>, Map<String, Object>> {
    private final RunStore store;
    private final RunTransitions transitions;
    private final GitHubClient github;
    private final CopilotClient copilot;
    private final UsageMeter usage;
    private final Clock clock;
    private final BudgetEvaluator budgets = new BudgetEvaluator();
    private final FeedbackComposer feedback = new FeedbackComposer();

    public HumanFixTask() {
        this(Services.instance().runStore(), Wiring.transitions(), Services.instance().github(), Services.instance().copilot(),
                Wiring.usageMeter(), Services.instance().clock());
    }

    HumanFixTask(RunStore store, RunTransitions transitions, GitHubClient github, CopilotClient copilot, UsageMeter usage, Clock clock) {
        this.store = store;
        this.transitions = transitions;
        this.github = github;
        this.copilot = copilot;
        this.usage = usage;
        this.clock = clock;
    }

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        String key = TaskSupport.ticketKey(input);
        transitions.moveTo(key, RunState.HUMAN_FIX, Actor.HUMAN, "Reviewer requested changes");
        Run run = store.get(key).orElseThrow();
        BudgetVerdict verdict = budgets.evaluate(run.budgets(), run.usage(), clock.instant(), BudgetEvaluator.IterationKind.HUMAN);
        if (verdict.isBreach()) {
            return TaskSupport.decision("ESCALATE", "Human review requested changes " + run.usage().humanIterations()
                    + " times; the ticket may be underspecified");
        }
        if (verdict.level() == BudgetVerdict.Level.WARN) {
            usage.warnOnce(key, "budget-warn", "⚠️ Approaching limits: " + String.join("; ", verdict.reasons()));
        }
        long reviewId = reviewId(TaskSupport.signal(input));
        List<ReviewComment> comments = reviewId > 0 ? github.listReviewCommentsForReview(run.repo(), run.prNumber(), reviewId) : List.of();
        String body = github.listReviews(run.repo(), run.prNumber()).stream()
                .filter(r -> r.id() == reviewId).map(Review::body).findFirst().orElse("");
        copilot.instruct(run.repo(), run.prNumber(), feedback.forHumanReview(comments, body));
        usage.recordHumanIteration(key);
        usage.recordCopilotSession(key);
        return TaskSupport.decision("CONTINUE", "Review feedback sent to Copilot");
    }

    private static long reviewId(Map<String, Object> signal) {
        Object v = signal.get("reviewId");
        return v instanceof Number n ? n.longValue() : 0L;
    }
}
