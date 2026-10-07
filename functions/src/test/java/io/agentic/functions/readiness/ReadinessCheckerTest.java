package io.agentic.functions.readiness;

import io.agentic.functions.support.Tickets;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ReadinessCheckerTest {
    private final ReadinessChecker checker = new ReadinessChecker();
    private final Set<String> allow = Set.of("acme/payments");

    @Test
    void validTicketHasNoReasons() {
        assertThat(checker.check(Tickets.ready(), allow, 5)).isEmpty();
    }

    @Test
    void repoNotOnboarded() {
        assertThat(checker.check(Tickets.with("acme/other", "- a", 3.0, List.of()), allow, 5))
                .containsExactly("Target repo acme/other is not onboarded for agentic work");
    }

    @Test
    void malformedRepo() {
        assertThat(checker.check(Tickets.with("payments", "- a", 3.0, List.of()), allow, 5))
                .singleElement().asString().contains("owner/repo form");
    }

    @Test
    void missingAc() {
        assertThat(checker.check(Tickets.with("acme/payments", " ", 3.0, List.of()), allow, 5)).containsExactly("No acceptance criteria found");
    }

    @Test
    void tooManyPoints() {
        assertThat(checker.check(Tickets.with("acme/payments", "- a", 8.0, List.of()), allow, 5))
                .containsExactly("Story points 8 exceed the agentic limit of 5; split the ticket");
    }

    @Test
    void missingPoints() {
        assertThat(checker.check(Tickets.with("acme/payments", "- a", null, List.of()), allow, 5)).containsExactly("Story points are not set");
    }

    @Test
    void multiRepoComponent() {
        assertThat(checker.check(Tickets.with("acme/payments", "- a", 3.0, List.of("Multi-Repo")), allow, 5))
                .containsExactly("Ticket spans multiple repos (component 'multi-repo')");
    }
}
