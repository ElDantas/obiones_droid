package io.agentic.functions.run;

import io.agentic.core.run.Actor;
import io.agentic.core.run.RunState;
import io.agentic.functions.ingress.Identities;
import io.agentic.functions.store.Run;
import io.agentic.functions.store.RunStore;
import io.agentic.integrations.github.GitHubClient;
import io.agentic.integrations.github.model.PullRequest;
import io.agentic.integrations.jira.JiraClient;
import software.amazon.awssdk.services.sfn.SfnClient;
import software.amazon.awssdk.services.sfn.model.StopExecutionRequest;

import java.util.Optional;

public class AbortService {
    public interface Mover {
        void moveTo(String ticketKey, RunState to, Actor actor, String reason);
    }

    private final RunStore runStore;
    private final SfnClient sfn;
    private final GitHubClient github;
    private final JiraClient jira;
    private final Identities identities;
    private final Mover mover;

    public AbortService(RunStore runStore, SfnClient sfn, GitHubClient github, JiraClient jira, Identities identities, Mover mover) {
        this.runStore = runStore;
        this.sfn = sfn;
        this.github = github;
        this.jira = jira;
        this.identities = identities;
        this.mover = mover;
    }

    public boolean abort(String ticketKey, Actor actor, String reason) {
        return abort(ticketKey, actor, reason, true);
    }

    public boolean abort(String ticketKey, Actor actor, String reason, boolean stopExecution) {
        Optional<Run> found = runStore.get(ticketKey);
        if (found.isEmpty() || found.get().state().isTerminal()) {
            return false;
        }
        Run run = found.get();
        if (stopExecution && run.executionArn() != null) {
            try {
                sfn.stopExecution(StopExecutionRequest.builder().executionArn(run.executionArn()).cause(reason).build());
            } catch (RuntimeException e) {
                System.err.println("StopExecution failed for " + ticketKey + ": " + e.getMessage());
            }
        }
        if (run.prNumber() != null && run.repo() != null && !run.repo().isEmpty()) {
            try {
                PullRequest pr = github.getPullRequest(run.repo(), run.prNumber());
                if ("open".equals(pr.state()) && identities.isCopilot(pr.authorLogin())) {
                    github.closePullRequest(run.repo(), run.prNumber());
                }
            } catch (RuntimeException e) {
                System.err.println("Closing PR failed for " + ticketKey + ": " + e.getMessage());
            }
        }
        mover.moveTo(ticketKey, RunState.ABORTED, actor, reason);
        try {
            jira.comment(ticketKey, "Agent run aborted: " + reason);
        } catch (RuntimeException e) {
            System.err.println("Jira comment failed for " + ticketKey + ": " + e.getMessage());
        }
        return true;
    }
}
