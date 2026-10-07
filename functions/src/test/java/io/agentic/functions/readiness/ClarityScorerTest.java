package io.agentic.functions.readiness;

import io.agentic.functions.support.Tickets;
import io.agentic.integrations.llm.FakeLlm;
import io.agentic.integrations.llm.Prompt;
import io.agentic.integrations.llm.TextModel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClarityScorerTest {

    @Test
    void bedrockFailureFailsOpenWithScore100() {
        TextModel model = mock(TextModel.class);
        when(model.converseJson(anyString(), any(Prompt.class), anyString(), eq(ClarityScorer.Clarity.class))).thenThrow(new RuntimeException("throttled"));
        assertThat(new ClarityScorer(model, () -> "m").score(Tickets.ready()).score()).isEqualTo(100);
    }

    @Test
    void usesClarityPromptWithFakeModel() {
        ClarityScorer scorer = new ClarityScorer(new FakeLlm(), () -> "m");
        assertThat(scorer.score(Tickets.ready()).score()).isEqualTo(90);
        assertThat(scorer.score(Tickets.with("acme/payments", "VAGUE", 3.0, java.util.List.of())).gaps()).isNotEmpty();
    }
}
