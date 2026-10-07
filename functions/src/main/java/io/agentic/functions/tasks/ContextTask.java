package io.agentic.functions.tasks;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import io.agentic.core.run.Actor;
import io.agentic.core.run.RunState;
import io.agentic.functions.config.Services;
import io.agentic.functions.config.Wiring;
import io.agentic.functions.context.IssueComposer;
import io.agentic.functions.context.Lesson;
import io.agentic.functions.context.LessonProvider;
import io.agentic.functions.notify.RunTransitions;
import io.agentic.functions.readiness.RepoConfig;
import io.agentic.functions.readiness.RepoConfigLoader;
import io.agentic.functions.store.Run;
import io.agentic.functions.store.RunStore;
import io.agentic.integrations.github.GitHubClient;
import io.agentic.integrations.jira.JiraClient;
import io.agentic.integrations.jira.JiraTicket;

import java.util.List;
import java.util.Map;

public class ContextTask implements RequestHandler<Map<String, Object>, Map<String, Object>> {
    private final RunStore store;
    private final RunTransitions transitions;
    private final JiraClient jira;
    private final GitHubClient github;
    private final RepoConfigLoader configLoader;
    private final LessonProvider lessons;
    private final IssueComposer composer = new IssueComposer();

    public ContextTask() {
        this(Services.instance().runStore(), Wiring.transitions(), Services.instance().jira(), Services.instance().github(),
                new RepoConfigLoader(Services.instance().github()),
                new io.agentic.functions.memory.MemoryLessonProvider(Services.instance().hybridSearch(), Services.instance().lessons(), Services.instance().clock()));
    }

    ContextTask(RunStore store, RunTransitions transitions, JiraClient jira, GitHubClient github, RepoConfigLoader configLoader, LessonProvider lessons) {
        this.store = store;
        this.transitions = transitions;
        this.jira = jira;
        this.github = github;
        this.configLoader = configLoader;
        this.lessons = lessons;
    }

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        String key = TaskSupport.ticketKey(input);
        transitions.moveTo(key, RunState.CONTEXT, Actor.BOT, "Ticket is agent-ready");
        Run run = store.get(key).orElseThrow();
        if (run.issueNumber() != null) {
            return Map.of("issueNumber", run.issueNumber());
        }
        JiraTicket ticket = jira.getTicket(key);
        RepoConfig config = configLoader.load(run.repo(), run.budgets());
        List<Lesson> found = lessons.lessonsFor(ticket, run.repo());
        IssueComposer.ComposedIssue issue = composer.compose(ticket, config, found, jira.browseUrl(key));
        int number = github.createIssue(run.repo(), issue.title(), issue.body());
        store.setIssue(key, number);
        store.setInjectedLessons(key, found.stream().map(Lesson::id).toList());
        return Map.of("issueNumber", number);
    }
}
