package io.agentic.functions.learning;

import io.agentic.integrations.github.GitHubClient;
import io.agentic.memory.LessonRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PromotionJobTest {
    private final LessonRepository repo = mock(LessonRepository.class);
    private final GitHubClient github = mock(GitHubClient.class);
    private final PromotionJob job = new PromotionJob(repo, github);

    @Test
    void opensOnePrPerCandidateWithLessonId() {
        var l = PromotionPolicyTest.lesson(3, 3, 0, Instant.now(), List.of());
        when(repo.find(anyString(), eq(Map.of()))).thenReturn(List.of(l, PromotionPolicyTest.lesson(3, 0, 0, Instant.now(), List.of())));
        when(github.countOpenPullRequestsMentioning("acme/payments", "Lesson-Id: " + l.id())).thenReturn(0);
        when(github.defaultBranch("acme/payments")).thenReturn("main");
        when(github.branchSha("acme/payments", "main")).thenReturn("base-sha");
        when(github.readFile("acme/payments", ".github/instructions/orders.instructions.md", "main")).thenReturn(Optional.empty());
        when(github.fileSha("acme/payments", ".github/instructions/orders.instructions.md", "main")).thenReturn(Optional.empty());
        when(github.openPullRequest(eq("acme/payments"), eq("agentic/lesson-11111111"), eq("main"), anyString(), anyString())).thenReturn(77);

        Map<String, Object> out = job.handleRequest(Map.of(), null);

        assertThat(out).containsEntry("candidates", 1).containsEntry("opened", 1);
        verify(github).createBranch("acme/payments", "agentic/lesson-11111111", "base-sha");
        verify(github).putFile(eq("acme/payments"), eq(".github/instructions/orders.instructions.md"), eq("agentic/lesson-11111111"),
                argThat(c -> c.contains("applyTo: \"src/**\"") && c.contains("- **t:** l")), anyString(), eq(null));
        verify(github).openPullRequest(eq("acme/payments"), eq("agentic/lesson-11111111"), eq("main"), anyString(), argThat(b -> b.contains("Lesson-Id: " + l.id())));
        verify(github).addLabels("acme/payments", 77, List.of(PromotionJob.LABEL));
    }

    @Test
    void skipsWhenAnOpenPrExists() {
        var l = PromotionPolicyTest.lesson(3, 3, 0, Instant.now(), List.of());
        when(github.countOpenPullRequestsMentioning("acme/payments", "Lesson-Id: " + l.id())).thenReturn(1);
        assertThat(job.promote(l)).isFalse();
        verify(github, never()).createBranch(anyString(), anyString(), anyString());
    }
}
