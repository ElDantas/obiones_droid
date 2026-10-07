package io.agentic.functions.ingress;

import io.agentic.functions.config.Services;
import io.agentic.functions.run.AbortService;
import io.agentic.functions.run.RunStarter;
import io.agentic.functions.run.SignalDispatcher;

public final class Wiring {
    private Wiring() {
    }

    public static Identities identities() {
        Services s = Services.instance();
        return new Identities(s.serviceUserLogin(), s.params().find("/agentic/github/appBot").orElse("agentic-bot[bot]"));
    }

    public static RunStarter runStarter() {
        Services s = Services.instance();
        return new RunStarter(s.params(), s.runStore(), s.sfn(), s.clock(), s::stateMachineArn);
    }

    public static AbortService abortService() {
        Services s = Services.instance();
        return new AbortService(s.runStore(), s.sfn(), s.github(), s.jira(), identities(), s.runStore()::moveTo);
    }

    public static GitHubEventRouter router() {
        Services s = Services.instance();
        return new GitHubEventRouter(s.runStore(), s.github(), identities(), abortService());
    }

    public static SignalDispatcher dispatcher() {
        Services s = Services.instance();
        return new SignalDispatcher(s.runStore(), s.sfn());
    }
}
