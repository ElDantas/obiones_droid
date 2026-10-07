package io.agentic.core.scope;

import io.agentic.core.budget.Budgets;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;

public final class ScopeGuard {

    public record ChangedFile(String path, int additions, int deletions) {
    }

    public List<String> check(Budgets b, List<ChangedFile> files) {
        List<String> violations = new ArrayList<>();
        int lines = files.stream().mapToInt(f -> f.additions() + f.deletions()).sum();
        if (lines > b.maxDiffLines()) {
            violations.add("Diff has " + lines + " lines (max " + b.maxDiffLines() + "); split the ticket");
        }
        if (files.size() > b.maxDiffFiles()) {
            violations.add("Diff touches " + files.size() + " files (max " + b.maxDiffFiles() + "); split the ticket");
        }
        for (String pattern : b.forbiddenPaths()) {
            PathMatcher m = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
            for (ChangedFile f : files) {
                if (m.matches(Path.of(f.path())) || m.matches(Path.of("/" + f.path()))) {
                    violations.add("Forbidden path changed: " + f.path() + " (rule " + pattern + ")");
                }
            }
        }
        return violations;
    }
}
