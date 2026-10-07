package io.agentic.functions.escalation;

import io.agentic.integrations.llm.FakeLlm;
import io.agentic.integrations.llm.Prompt;
import io.agentic.integrations.llm.TextModel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DiagnoserTest {

    @Test
    void failureReturnsUnavailable() {
        TextModel model = mock(TextModel.class);
        when(model.converse(anyString(), any(Prompt.class), anyString())).thenThrow(new RuntimeException("down"));
        assertThat(new Diagnoser(model, () -> "m").diagnose("stuck", List.of(), List.of(), "")).isEqualTo(Diagnoser.UNAVAILABLE);
    }

    @Test
    void fakeModelProducesText() {
        assertThat(new Diagnoser(new FakeLlm(), () -> "m").diagnose("Same failure repeated", List.of("expected 50"), List.of("fix"), "- cap"))
                .contains("same check keeps failing");
    }
}
