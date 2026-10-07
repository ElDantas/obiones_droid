package io.agentic.functions.context;

import io.agentic.core.budget.Budgets;
import io.agentic.functions.readiness.RepoConfig;
import io.agentic.integrations.jira.JiraTicket;

import java.util.List;

public final class IssueComposer {

    public record ComposedIssue(String title, String body) {
    }

    public ComposedIssue compose(JiraTicket t, RepoConfig config, List<Lesson> lessons, String jiraLink) {
        Budgets b = config.budgets();
        StringBuilder sb = new StringBuilder();
        sb.append("> Created by agentic-bot from Jira [").append(t.key()).append("](").append(jiraLink)
                .append("). Do not edit; change the Jira ticket and comment `/agent restart`.\n\n");
        sb.append("## Goal\n").append(t.summary()).append("\n\n");
        sb.append("## Description\n").append(blankAsNone(t.description())).append("\n\n");
        sb.append("## Acceptance criteria\n").append(asBullets(t.acceptanceCriteria())).append("\n\n");
        sb.append("## Constraints\n");
        sb.append("- Do not modify files matching: ").append(String.join(", ", b.forbiddenPaths())).append('\n');
        sb.append("- Keep the change under ").append(b.maxDiffLines()).append(" changed lines and ").append(b.maxDiffFiles()).append(" files.\n");
        sb.append("- Every acceptance criterion must be covered by an automated test.\n");
        sb.append("- Follow `.github/copilot-instructions.md` and any `.github/instructions/*.instructions.md`.\n\n");
        sb.append("## References\n");
        if (t.confluenceLinks().isEmpty()) {
            sb.append("None\n\n");
        } else {
            t.confluenceLinks().forEach(l -> sb.append("- ").append(l).append('\n'));
            sb.append('\n');
        }
        sb.append("## Lessons from past work\n");
        if (lessons.isEmpty()) {
            sb.append("None yet\n");
        } else {
            lessons.forEach(l -> sb.append("- **").append(l.trigger()).append("** → ").append(l.lesson()).append('\n'));
        }
        return new ComposedIssue("[" + t.key() + "] " + t.summary(), sb.toString());
    }

    static String asBullets(String text) {
        if (text == null || text.isBlank()) {
            return "None";
        }
        StringBuilder sb = new StringBuilder();
        for (String line : text.strip().split("\n")) {
            String l = line.strip();
            if (l.isEmpty()) {
                continue;
            }
            sb.append(l.startsWith("- ") || l.startsWith("* ") ? "- " + l.substring(2) : "- " + l).append('\n');
        }
        return sb.toString().stripTrailing();
    }

    private static String blankAsNone(String s) {
        return s == null || s.isBlank() ? "None" : s;
    }
}
