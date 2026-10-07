package io.agentic.functions.notify;

import io.agentic.core.run.RunState;
import io.agentic.core.text.Scrubber;
import io.agentic.functions.store.Run;

import java.util.Optional;

public final class Messages {
    private Messages() {
    }

    public static String root(Run run, String summary, Links links) {
        return "🤖 *" + run.ticketKey() + "* · " + Scrubber.scrub(summary) + " · `" + run.repo() + "` · " + links.ticket(run.ticketKey());
    }

    public static Optional<String> slack(Run run, RunState to, String reason, Links links) {
        String r = Scrubber.scrub(reason == null ? "" : reason);
        int iteration = run.usage().gateIterations() + 1;
        return Optional.ofNullable(switch (to) {
            case NEEDS_INFO -> "ℹ️ Needs info: " + r;
            case CONTEXT -> "📝 Preparing the GitHub issue";
            case CODING -> "⌨️ Copilot is coding: " + links.issue(run.repo(), run.issueNumber());
            case GATES -> "🔍 Running gates (iteration " + iteration + ")";
            case FIXING -> "🔧 Fixing: " + r;
            case HUMAN_REVIEW -> "👀 Ready for review: " + links.pr(run.repo(), run.prNumber());
            case HUMAN_FIX -> "🔁 Addressing review comments";
            case ESCALATED -> "⚠️ Paused: " + r + "\nTo abort, remove the `Agentic AI Approved` label. To retry from scratch, comment `/agent restart` on the Jira ticket.";
            case DONE -> "✅ Merged: " + links.pr(run.repo(), run.prNumber());
            case ABORTED -> "🛑 Aborted: " + r;
            case READINESS -> null;
        });
    }

    public static Optional<String> jira(Run run, RunState to, String reason, Links links, String agentReadyUrl) {
        String r = Scrubber.scrub(reason == null ? "" : reason);
        return Optional.ofNullable(switch (to) {
            case NEEDS_INFO -> "This ticket is not ready for the agent:\n" + bullets(r)
                    + (agentReadyUrl == null ? "" : "\nSee the Definition of Agent-Ready: " + agentReadyUrl);
            case CODING -> "Agent is implementing in " + links.issue(run.repo(), run.issueNumber());
            case HUMAN_REVIEW -> "PR ready for review: " + links.pr(run.repo(), run.prNumber());
            case ESCALATED -> "Agent run paused: " + r;
            case DONE -> "Merged: " + links.pr(run.repo(), run.prNumber());
            case ABORTED -> "Agent run aborted: " + r;
            default -> null;
        });
    }

    static String bullets(String reasons) {
        StringBuilder sb = new StringBuilder();
        for (String part : reasons.split(";\\s*")) {
            if (!part.isBlank()) {
                sb.append("- ").append(part.strip()).append('\n');
            }
        }
        return sb.toString().stripTrailing();
    }
}
