package io.agentic.functions.learning;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import io.agentic.functions.config.Services;
import io.agentic.integrations.github.GitHubClient;
import io.agentic.memory.LessonRecord;
import io.agentic.memory.LessonRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class PromotionJob implements RequestHandler<Map<String, Object>, Map<String, Object>> {
    public static final String LABEL = "agentic-lesson-promotion";

    private final LessonRepository repository;
    private final GitHubClient github;
    private final InstructionFileWriter writer = new InstructionFileWriter();

    public PromotionJob() {
        this(Services.instance().lessons(), Services.instance().github());
    }

    PromotionJob(LessonRepository repository, GitHubClient github) {
        this.repository = repository;
        this.github = github;
    }

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        List<LessonRecord> candidates = repository.find("status = 'active' AND repo IS NOT NULL AND hits >= 3 ORDER BY hits DESC LIMIT 50", Map.of())
                .stream().filter(PromotionPolicy::isCandidate).toList();
        int opened = 0;
        for (LessonRecord l : candidates) {
            try {
                if (promote(l)) {
                    opened++;
                }
            } catch (RuntimeException e) {
                System.err.println("WARN promotion failed for " + l.id() + ": " + e.getMessage());
            }
        }
        return Map.of("candidates", candidates.size(), "opened", opened);
    }

    boolean promote(LessonRecord l) {
        if (github.countOpenPullRequestsMentioning(l.repo(), "Lesson-Id: " + l.id()) > 0) {
            return false;
        }
        String area = InstructionFileWriter.area(l);
        String path = ".github/instructions/" + area + ".instructions.md";
        String base = github.defaultBranch(l.repo());
        String branch = "agentic/lesson-" + l.id().substring(0, 8);
        github.createBranch(l.repo(), branch, github.branchSha(l.repo(), base));
        Optional<String> existing = github.readFile(l.repo(), path, base);
        Optional<String> existingSha = github.fileSha(l.repo(), path, base);
        github.putFile(l.repo(), path, branch, writer.append(existing.orElse(null), area, InstructionFileWriter.applyTo(l), l),
                "chore(agentic): promote lesson \"" + l.trigger() + "\"", existingSha.orElse(null));
        int pr = github.openPullRequest(l.repo(), branch, base, "chore(agentic): promote lesson \"" + l.trigger() + "\"", body(l));
        github.addLabels(l.repo(), pr, List.of(LABEL));
        return true;
    }

    static String body(LessonRecord l) {
        StringBuilder sb = new StringBuilder();
        sb.append("This lesson has recurred ").append(l.hits()).append(" times and helped in ").append(l.helped())
                .append(" of them, so `agentic-bot` proposes making it a permanent rule for Copilot.\n\n");
        sb.append("**When:** ").append(l.trigger()).append("\n\n**Rule:** ").append(l.lesson()).append("\n\n");
        if (!l.evidence().isEmpty()) {
            sb.append("**Evidence:**\n");
            l.evidence().stream().limit(10).forEach(e -> sb.append("- ").append(e).append('\n'));
            sb.append('\n');
        }
        sb.append("Merge to adopt the rule; close to reject it (it will not be proposed again).\n\n");
        sb.append("Lesson-Id: ").append(l.id()).append('\n');
        return sb.toString();
    }
}
