package io.agentic.infra;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.awscdk.App;
import software.amazon.awscdk.assertions.Template;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OrchestratorStackTest {
    private static Template template;
    private static JsonNode states;

    @BeforeAll
    static void synth() throws Exception {
        OrchestratorStack stack = new OrchestratorStack(new App(), "O", null, Map.of(), ApiStackTest.settings(false));
        template = Template.fromStack(stack);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode json = mapper.valueToTree(template.toJSON());
        JsonNode machine = null;
        for (JsonNode r : json.path("Resources")) {
            if ("AWS::StepFunctions::StateMachine".equals(r.path("Type").asText())) {
                machine = r;
            }
        }
        StringBuilder definition = new StringBuilder();
        for (JsonNode part : machine.at("/Properties/DefinitionString/Fn::Join/1")) {
            definition.append(part.isTextual() ? part.asText() : "TOKEN");
        }
        states = mapper.readTree(definition.toString()).path("States");
    }

    @Test
    void containsEveryStateFromTheFlow() {
        assertThat(states.fieldNames()).toIterable().contains(
                "Readiness", "ReadyChoice", "NeedsInfo", "Context", "AssignCopilot", "WaitPrReady", "AmbiguousChoice",
                "WaitChecks", "EvaluateGates", "GateChoice", "Fixing", "FixChoice", "WaitPrUpdated", "MarkHumanReview",
                "WaitHumanOutcome", "OutcomeChoice", "RemindReviewers", "HumanFix", "HumanFixChoice", "Complete", "Abort",
                "Escalate", "WaitDecision", "ApplyDecision", "ResumeChoice", "End",
                "EscFromResult", "EscCodingTimeout", "EscChecksTimeout", "EscAmbiguousPr", "EscInternalError",
                "AbortPrClosed", "AbortSla", "AbortChosen");
    }

    @Test
    void waitStatesUseTaskTokensAndTimeouts() {
        assertThat(states.at("/WaitDecision/TimeoutSeconds").asInt()).isEqualTo(86400);
        assertThat(states.at("/WaitChecks/TimeoutSeconds").asInt()).isEqualTo(3600);
        assertThat(states.at("/WaitHumanOutcome/TimeoutSeconds").asInt()).isEqualTo(259200);
        assertThat(states.at("/WaitPrReady/TimeoutSecondsPath").asText()).isEqualTo("$.readiness.codingTimeoutSeconds");
        for (String w : new String[]{"WaitPrReady", "WaitChecks", "WaitPrUpdated", "WaitHumanOutcome", "WaitDecision"}) {
            assertThat(states.at("/" + w + "/Resource").asText()).endsWith(":states:::lambda:invoke.waitForTaskToken");
        }
    }

    @Test
    void timeoutsRouteToTheRightStates() {
        assertThat(states.at("/WaitDecision/Catch/0/Next").asText()).isEqualTo("AbortSla");
        assertThat(states.at("/WaitHumanOutcome/Catch/0/Next").asText()).isEqualTo("RemindReviewers");
        assertThat(states.at("/WaitChecks/Catch/0/Next").asText()).isEqualTo("EscChecksTimeout");
    }

    @Test
    void resumeGatesGoesStraightToEvaluation() {
        boolean found = false;
        for (JsonNode c : states.at("/ResumeChoice/Choices")) {
            if ("RESUME_GATES".equals(c.path("StringEquals").asText())) {
                assertThat(c.path("Next").asText()).isEqualTo("EvaluateGates");
                found = true;
            }
        }
        assertThat(found).isTrue();
    }

    @Test
    void publishesArnParameter() {
        template.hasResourceProperties("AWS::SSM::Parameter", Map.of("Name", "/agentic/stateMachineArn"));
    }
}
