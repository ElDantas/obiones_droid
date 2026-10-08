package io.agentic.functions.ops;

import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import io.agentic.integrations.slack.SlackClient;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OpsAlertHandlerTest {
    private final SlackClient slack = mock(SlackClient.class);
    private final OpsAlertHandler handler = new OpsAlertHandler(() -> slack, () -> "#ops", "eu-west-2");

    @Test
    void alarmBecomesSlackMessageWithName() {
        String msg = "{\"AlarmName\":\"agentic-ExecutionsFailed\",\"NewStateValue\":\"ALARM\",\"NewStateReason\":\"Threshold crossed\"}";
        SNSEvent e = new SNSEvent().withRecords(List.of(new SNSEvent.SNSRecord().withSns(new SNSEvent.SNS().withMessage(msg))));
        handler.handleRequest(e, null);
        verify(slack).post(eq("#ops"), isNull(), isNull(), argThat(t -> t.startsWith("🚨 *agentic-ExecutionsFailed* is ALARM") && t.contains("alarm/agentic-ExecutionsFailed")));
    }

    @Test
    void parameterChangeIsDescribed() {
        assertThat(handler.format("{\"detail-type\":\"Parameter Store Change\",\"detail\":{\"name\":\"/agentic/enabled\",\"operation\":\"Update\"}}"))
                .isEqualTo("🔌 Kill switch parameter */agentic/enabled* changed (Update)");
    }

    @Test
    void plainTextFallsBack() {
        assertThat(handler.format("hello")).isEqualTo("🚨 hello");
    }
}
