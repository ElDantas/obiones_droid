package io.agentic.functions.gates;

import io.agentic.core.text.Scrubber;
import io.agentic.functions.readiness.RepoConfig;
import io.agentic.integrations.github.model.ReviewComment;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FeedbackComposer {
    public static final int MAX_FINDINGS = 30;
    private static final Map<String, String> HEADINGS = Map.of(
            "ci", "CI",
            "copilot-review", "Copilot review",
            "ac-review", "Acceptance criteria review",
            "qa", "QA: missing tests");

    public String forGates(GateFindings f, RepoConfig config) {
        String sha = f.headSha() == null ? "" : f.headSha().substring(0, Math.min(7, f.headSha().length()));
        StringBuilder sb = new StringBuilder("@copilot The automated gates failed on commit ").append(sha)
                .append(". Fix all of the following in one change, then request review again.\n");
        Map<String, StringBuilder> groups = new LinkedHashMap<>();
        int shown = 0;
        for (GateFinding finding : f.blocking()) {
            if (shown == MAX_FINDINGS) {
                break;
            }
            groups.computeIfAbsent(finding.gate(), g -> new StringBuilder()).append("- ").append(line(finding)).append('\n');
            shown++;
        }
        groups.forEach((gate, lines) -> sb.append("\n### ").append(HEADINGS.getOrDefault(gate, gate)).append('\n').append(lines));
        if (f.blocking().size() > MAX_FINDINGS) {
            sb.append("\n…and ").append(f.blocking().size() - MAX_FINDINGS).append(" more\n");
        }
        if (!f.advisory().isEmpty()) {
            sb.append("\n### Advisory (fix if relevant)\n");
            f.advisory().stream().limit(10).forEach(a -> sb.append("- ").append(line(a)).append('\n'));
        }
        sb.append("\nDo not modify files matching: ").append(String.join(", ", config.budgets().forbiddenPaths()));
        return Scrubber.scrub(sb.toString());
    }

    public String forHumanReview(List<ReviewComment> comments, String reviewBody) {
        StringBuilder sb = new StringBuilder("@copilot A reviewer requested changes. Address every point below in one change, then request review again.\n");
        if (reviewBody != null && !reviewBody.isBlank()) {
            sb.append("\n### Review summary\n").append(reviewBody.strip()).append('\n');
        }
        if (!comments.isEmpty()) {
            sb.append("\n### Inline comments\n");
            for (ReviewComment c : comments) {
                sb.append("- ");
                if (c.path() != null) {
                    sb.append('`').append(c.path()).append(c.line() == null ? "" : ":" + c.line()).append("` — ");
                }
                sb.append(c.body().strip().replace("\n", " ")).append('\n');
            }
        }
        return Scrubber.scrub(sb.toString().stripTrailing());
    }

    private static String line(GateFinding f) {
        StringBuilder sb = new StringBuilder();
        if (f.gate().equals("ci")) {
            sb.append('`').append(checkName(f.id())).append("` — ");
        }
        if (f.file() != null) {
            sb.append(f.file());
            if (f.line() != null) {
                sb.append(':').append(f.line());
            }
            sb.append(" — ");
        }
        sb.append(f.message() == null ? "" : f.message().strip().replace("\n", " "));
        return sb.toString();
    }

    private static String checkName(String id) {
        String[] parts = id.split(":", 3);
        return parts.length > 1 ? parts[1] : id;
    }
}
