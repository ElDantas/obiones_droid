package io.agentic.functions.config;

import io.agentic.functions.gates.GateCollector;
import io.agentic.functions.ingress.GitHubEventRouter;
import io.agentic.functions.ingress.Identities;
import io.agentic.functions.notify.JiraNotifier;
import io.agentic.functions.notify.Links;
import io.agentic.functions.notify.RunTransitions;
import io.agentic.functions.notify.SlackNotifier;
import io.agentic.functions.run.AbortService;
import io.agentic.functions.run.RunStarter;
import io.agentic.functions.run.SignalDispatcher;

import java.util.List;

public final class Wiring {
    private static SlackNotifier slackNotifier;
    private static RunTransitions transitions;

    private Wiring() {
    }

    public static Identities identities() {
        Services s = Services.instance();
        return new Identities(s.serviceUserLogin(), s.params().find("/agentic/github/appBot").orElse("agentic-bot[bot]"));
    }

    public static Links links() {
        Services s = Services.instance();
        String web = s.params().find("/agentic/github/webUrl").orElse("https://github.com");
        return new Links(web, s.jira().browseUrl("").replaceFirst("/browse/$", ""));
    }

    public static synchronized SlackNotifier slackNotifier() {
        if (slackNotifier == null) {
            Services s = Services.instance();
            slackNotifier = new SlackNotifier(s.slack(), s.jira(), s.runStore(),
                    () -> s.params().find("/agentic/slack/channels/dev").orElse("#agentic-dev"), links());
        }
        return slackNotifier;
    }

    public static synchronized RunTransitions transitions() {
        if (transitions == null) {
            Services s = Services.instance();
            JiraNotifier jira = new JiraNotifier(s.jira(), s.slack(),
                    () -> s.params().find("/agentic/slack/channels/ops").orElse("#agentic-ops"),
                    () -> s.params().find("/agentic/docs/agentReadyUrl").filter(u -> u.startsWith("http")).orElse(null),
                    links());
            transitions = new RunTransitions(s.runStore(), List.of(slackNotifier(), jira));
        }
        return transitions;
    }

    public static GateCollector gateCollector() {
        return new GateCollector(Services.instance().github(), identities());
    }

    public static RunStarter runStarter() {
        Services s = Services.instance();
        return new RunStarter(s.params(), s.runStore(), s.sfn(), s.clock(), s::stateMachineArn);
    }

    public static AbortService abortService() {
        Services s = Services.instance();
        return new AbortService(s.runStore(), s.sfn(), s.github(), identities(), transitions()::moveTo);
    }

    public static GitHubEventRouter router() {
        Services s = Services.instance();
        return new GitHubEventRouter(s.runStore(), s.github(), identities(), abortService(), new io.agentic.functions.readiness.RepoConfigLoader(s.github()));
    }

    public static SignalDispatcher dispatcher() {
        Services s = Services.instance();
        return new SignalDispatcher(s.runStore(), s.sfn());
    }
}
