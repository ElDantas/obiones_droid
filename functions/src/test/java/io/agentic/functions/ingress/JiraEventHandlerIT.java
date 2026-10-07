package io.agentic.functions.ingress;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import io.agentic.functions.config.Params;
import io.agentic.functions.run.AbortService;
import io.agentic.functions.run.RunStarter;
import io.agentic.functions.store.RunStore;
import io.agentic.functions.support.DynamoDbLocal;
import io.agentic.integrations.slack.SlackClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sfn.SfnClient;
import software.amazon.awssdk.services.sfn.model.StartExecutionRequest;
import software.amazon.awssdk.services.sfn.model.StartExecutionResponse;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JiraEventHandlerIT {
    private SfnClient sfn;
    private Params params;
    private SlackClient slack;
    private AbortService abort;
    private JiraEventHandler handler;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-07T12:00:00Z"), ZoneOffset.UTC);
        RunStore store = new RunStore(DynamoDbLocal.freshClient(), clock);
        sfn = mock(SfnClient.class);
        when(sfn.startExecution(any(StartExecutionRequest.class))).thenReturn(StartExecutionResponse.builder().executionArn("arn:exec:1").build());
        params = mock(Params.class);
        when(params.isGloballyEnabled()).thenReturn(true);
        when(params.find("/agentic/budgets/defaults")).thenReturn(Optional.empty());
        slack = mock(SlackClient.class);
        abort = mock(AbortService.class);
        RunStarter starter = new RunStarter(params, store, sfn, clock, () -> "arn:sm");
        handler = new JiraEventHandler(() -> "secret-token", () -> starter, () -> abort, () -> slack, () -> "#ops");
    }

    private APIGatewayV2HTTPEvent event(String token, String body) {
        return APIGatewayV2HTTPEvent.builder().withHeaders(token == null ? Map.of() : Map.of("x-agentic-token", token)).withBody(body).build();
    }

    @Test
    void badTokenIsRejected() {
        APIGatewayV2HTTPResponse res = handler.handleRequest(event("wrong", "{\"ticketKey\":\"ABC-1\",\"event\":\"approved\"}"), null);
        assertThat(res.getStatusCode()).isEqualTo(401);
        verify(sfn, never()).startExecution(any(StartExecutionRequest.class));
    }

    @Test
    void approvedTwiceStartsExactlyOneExecution() {
        String body = "{\"ticketKey\":\"ABC-1\",\"event\":\"approved\"}";
        assertThat(handler.handleRequest(event("secret-token", body), null).getBody()).contains("STARTED");
        assertThat(handler.handleRequest(event("secret-token", body), null).getBody()).contains("ALREADY_RUNNING");
        verify(sfn, times(1)).startExecution(any(StartExecutionRequest.class));
    }

    @Test
    void killSwitchOffReturnsDisabledAndPostsToOps() {
        when(params.isGloballyEnabled()).thenReturn(false);
        APIGatewayV2HTTPResponse res = handler.handleRequest(event("secret-token", "{\"ticketKey\":\"ABC-2\",\"event\":\"approved\"}"), null);
        assertThat(res.getBody()).contains("DISABLED");
        verify(sfn, never()).startExecution(any(StartExecutionRequest.class));
        verify(slack).post(eq("#ops"), isNull(), isNull(), anyString());
    }

    @Test
    void unflaggedAborts() {
        when(abort.abort(eq("ABC-3"), any(), anyString())).thenReturn(true);
        APIGatewayV2HTTPResponse res = handler.handleRequest(event("secret-token", "{\"ticketKey\":\"ABC-3\",\"event\":\"unflagged\"}"), null);
        assertThat(res.getStatusCode()).isEqualTo(202);
        assertThat(res.getBody()).contains("ABORTED");
    }

    @Test
    void invalidKeyIsRejected() {
        assertThat(handler.handleRequest(event("secret-token", "{\"ticketKey\":\"../etc\",\"event\":\"approved\"}"), null).getStatusCode()).isEqualTo(400);
    }
}
