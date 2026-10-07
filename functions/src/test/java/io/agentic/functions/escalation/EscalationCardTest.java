package io.agentic.functions.escalation;

import io.agentic.core.run.RunState;
import io.agentic.functions.notify.Links;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.agentic.functions.support.Fixtures.run;
import static org.assertj.core.api.Assertions.assertThat;

class EscalationCardTest {
    private final Links links = new Links("https://github.com", "https://jira");

    @Test
    @SuppressWarnings("unchecked")
    void hasFourButtonsWithDecisionValues() {
        List<Map<String, Object>> blocks = EscalationCard.blocks(run("ABC-1", RunState.ESCALATED, 101, 418), "stuck", "cause", "e1", links);
        Map<String, Object> actions = blocks.stream().filter(b -> "actions".equals(b.get("type"))).findFirst().orElseThrow();
        List<Map<String, Object>> buttons = (List<Map<String, Object>>) actions.get("elements");
        assertThat(buttons).hasSize(4);
        assertThat(buttons).extracting(b -> (String) b.get("value"))
                .allSatisfy(v -> assertThat(v).contains("\"ticketKey\":\"ABC-1\"").contains("\"escalationId\":\"e1\""))
                .anySatisfy(v -> assertThat(v).contains("\"decision\":\"RAISE_BUDGET\""));
        assertThat(buttons.get(3)).containsKey("confirm").containsEntry("style", "danger");
    }

    @Test
    void diagnosisIsScrubbed() {
        List<Map<String, Object>> blocks = EscalationCard.blocks(run("ABC-1", RunState.ESCALATED, 101, 418), "stuck",
                "Ask jane.doe@corp.com about the rounding rule", "e1", links);
        assertThat(blocks.toString()).contains("[REDACTED_EMAIL]").doesNotContain("jane.doe@corp.com");
    }

    @Test
    void resolvedReplacesButtons() {
        List<Map<String, Object>> blocks = EscalationCard.blocks(run("ABC-1", RunState.ESCALATED, 101, 418), "stuck", "cause", "e1", links);
        List<Map<String, Object>> resolved = EscalationCard.resolved(blocks, "TAKE_OVER", "U1");
        assertThat(resolved).noneMatch(b -> "actions".equals(b.get("type")));
        assertThat(resolved.toString()).contains("✅ Take over by <@U1>");
    }
}
