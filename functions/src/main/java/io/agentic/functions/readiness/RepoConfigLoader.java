package io.agentic.functions.readiness;

import io.agentic.core.budget.Budgets;
import io.agentic.integrations.github.GitHubClient;
import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class RepoConfigLoader {
    public static final String PATH = ".github/agentic.yml";

    private final GitHubClient github;

    public RepoConfigLoader(GitHubClient github) {
        this.github = github;
    }

    public RepoConfig load(String repo, Budgets base) {
        Optional<String> text = github.readFile(repo, PATH);
        if (text.isEmpty()) {
            return new RepoConfig(base, List.of(), List.of(), List.of());
        }
        return parse(text.get(), base);
    }

    @SuppressWarnings("unchecked")
    public static RepoConfig parse(String yamlText, Budgets base) {
        Object loaded = new Yaml().load(yamlText);
        Map<String, Object> root = loaded instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        Map<String, Object> budgets = root.get("budgets") instanceof Map<?, ?> b ? (Map<String, Object>) b : Map.of();
        Budgets merged = base.merge(budgets);
        Set<String> forbidden = new LinkedHashSet<>(base.forbiddenPaths());
        forbidden.addAll(strings(root.get("forbiddenPaths")));
        merged = new Budgets(merged.maxGateIterations(), merged.maxHumanIterations(), merged.premiumSoft(), merged.premiumHard(),
                merged.codingTimeout(), merged.maxRunAge(), merged.actionsMinutesSoft(), merged.actionsMinutesHard(),
                merged.maxDiffLines(), merged.maxDiffFiles(), new ArrayList<>(forbidden));
        return new RepoConfig(merged, strings(root.get("reviewers")), strings(root.get("escalationApprovers")), strings(root.get("requiredChecks")));
    }

    private static List<String> strings(Object v) {
        if (!(v instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().filter(o -> o != null).map(Object::toString).toList();
    }
}
