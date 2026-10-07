package io.agentic.functions.readiness;

import io.agentic.core.budget.Budgets;
import io.agentic.integrations.github.GitHubClient;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RepoConfigLoaderTest {

    @Test
    void missingFileGivesDefaults() {
        GitHubClient gh = mock(GitHubClient.class);
        when(gh.readFile("acme/payments", ".github/agentic.yml")).thenReturn(Optional.empty());
        RepoConfig c = new RepoConfigLoader(gh).load("acme/payments", Budgets.defaults());
        assertThat(c.budgets()).isEqualTo(Budgets.defaults());
        assertThat(c.reviewers()).isEmpty();
    }

    @Test
    void overridesApplyAndDefaultsCannotBeRemoved() {
        RepoConfig c = RepoConfigLoader.parse("""
                budgets:
                  maxGateIterations: 5
                forbiddenPaths:
                  - "legacy/**"
                reviewers: [alice-gh]
                escalationApprovers: [alice@corp.com]
                requiredChecks: [build]
                """, Budgets.defaults());
        assertThat(c.budgets().maxGateIterations()).isEqualTo(5);
        assertThat(c.budgets().forbiddenPaths()).contains("infra/**", "**/migrations/**", "legacy/**");
        assertThat(c.reviewers()).containsExactly("alice-gh");
        assertThat(c.requiredChecks()).containsExactly("build");
    }

    @Test
    void budgetsLevelForbiddenPathsCannotDropDefaults() {
        RepoConfig c = RepoConfigLoader.parse("budgets:\n  forbiddenPaths: []\n", Budgets.defaults());
        assertThat(c.budgets().forbiddenPaths()).containsAll(Budgets.defaults().forbiddenPaths());
    }
}
