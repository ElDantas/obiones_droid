package io.agentic.functions.context;

import io.agentic.core.budget.Budgets;
import io.agentic.functions.readiness.RepoConfig;
import io.agentic.functions.support.Tickets;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IssueComposerTest {
    private final RepoConfig config = new RepoConfig(Budgets.defaults(), List.of(), List.of(), List.of());

    @Test
    void containsAllSections() {
        IssueComposer.ComposedIssue i = new IssueComposer().compose(Tickets.ready(), config, List.of(), "https://jira/browse/ABC-1");
        assertThat(i.title()).isEqualTo("[ABC-1] Cap discounts");
        assertThat(i.body()).contains("## Goal", "## Description", "## Acceptance criteria", "## Constraints", "## References", "## Lessons from past work")
                .contains("- Cap at 50%\n- Tax after cap")
                .contains("infra/**")
                .contains("under 800 changed lines and 25 files")
                .contains("None yet");
    }

    @Test
    void rendersLessons() {
        IssueComposer.ComposedIssue i = new IssueComposer().compose(Tickets.ready(), config,
                List.of(new Lesson("l1", "Accessing the database from a service", "Use the repository class", List.of())), "j");
        assertThat(i.body()).contains("- **Accessing the database from a service** → Use the repository class");
    }

    @Test
    void plainLinesBecomeBullets() {
        assertThat(IssueComposer.asBullets("first\n* second\n\n- third")).isEqualTo("- first\n- second\n- third");
    }
}
