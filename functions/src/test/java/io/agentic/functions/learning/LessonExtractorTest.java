package io.agentic.functions.learning;

import io.agentic.integrations.github.model.ReviewComment;
import io.agentic.integrations.llm.FakeLlm;
import io.agentic.integrations.llm.Prompt;
import io.agentic.integrations.llm.TextModel;
import io.agentic.memory.LessonDraft;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LessonExtractorTest {

    private static ReviewComment comment(String body) {
        return new ReviewComment(1, "alice", "src/main/java/OrderService.java", 3, body, "s", 9L);
    }

    @Test
    void nitIsNotALesson() {
        TextModel model = mock(TextModel.class);
        when(model.converseJson(anyString(), any(Prompt.class), anyString(), eq(LessonExtractor.Extracted.class)))
                .thenReturn(new LessonExtractor.Extracted(false, null, null, null));
        assertThat(new LessonExtractor(model, () -> "m").fromReviewComment("acme/payments", comment("nit: typo"), "pr")).isEmpty();
    }

    @Test
    void generalisableCommentBecomesScrubbedDraft() {
        TextModel model = mock(TextModel.class);
        when(model.converseJson(anyString(), any(Prompt.class), anyString(), eq(LessonExtractor.Extracted.class)))
                .thenReturn(new LessonExtractor.Extracted(true, "Accessing the database from a service", "Use the repository; ask jane.doe@corp.com", List.of("data-access")));
        Optional<LessonDraft> d = new LessonExtractor(model, () -> "m").fromReviewComment("acme/payments", comment("use the repo"), "https://pr/1");
        assertThat(d).hasValueSatisfying(x -> {
            assertThat(x.repo()).isEqualTo("acme/payments");
            assertThat(x.kind()).isEqualTo("review_feedback");
            assertThat(x.paths()).containsExactly("src/main/java");
            assertThat(x.component()).isEqualTo("src");
            assertThat(x.language()).isEqualTo("java");
            assertThat(x.lesson()).contains("[REDACTED_EMAIL]").doesNotContain("jane.doe");
            assertThat(x.evidence()).isEqualTo("https://pr/1");
        });
    }

    @Test
    void modelFailureYieldsNothing() {
        TextModel model = mock(TextModel.class);
        when(model.converseJson(anyString(), any(Prompt.class), anyString(), eq(LessonExtractor.Extracted.class))).thenThrow(new RuntimeException("x"));
        assertThat(new LessonExtractor(model, () -> "m").fromPostMortem("r", "stuck", "d", "RESUME", "pr")).isEmpty();
    }

    @Test
    void worksWithFakeModel() {
        assertThat(new LessonExtractor(new FakeLlm(), () -> "m").fromReviewComment("acme/payments", comment("Use the repository layer. Raw SQL is banned."), "pr"))
                .isPresent();
    }
}
