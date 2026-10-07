package io.agentic.functions.escalation;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import io.agentic.core.budget.Budgets;
import io.agentic.core.run.RunState;
import io.agentic.functions.ingress.HmacVerifier;
import io.agentic.functions.ingress.RoutedSignal;
import io.agentic.functions.readiness.RepoConfig;
import io.agentic.functions.readiness.RepoConfigLoader;
import io.agentic.functions.run.SignalDispatcher;
import io.agentic.functions.store.RunStore;
import io.agentic.functions.store.WaitKind;
import io.agentic.functions.support.Tickets;
import io.agentic.integrations.jira.JiraClient;
import io.agentic.integrations.slack.SlackClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.agentic.functions.support.Fixtures.run;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SlackActionsHandlerTest {
    private static final String SECRET = "slack-secret";
    private static final long NOW = 1_800_000_000L;
    private RunStore store;
    private SlackClient slack;
    private EscalationAuthorizer authorizer;
    private SignalDispatcher dispatcher;
    private SlackActionsHandler handler;

    @BeforeEach
    void setUp() {
        store = mock(RunStore.class);
        slack = mock(SlackClient.class);
        JiraClient jira = mock(JiraClient.class);
        RepoConfigLoader loader = mock(RepoConfigLoader.class);
        authorizer = mock(EscalationAuthorizer.class);
        dispatcher = mock(SignalDispatcher.class);
        when(store.get("ABC-1")).thenReturn(Optional.of(run("ABC-1", RunState.ESCALATED, 101, 418, RunState.FIXING)));
        when(jira.getTicket("ABC-1")).thenReturn(Tickets.ready());
        when(loader.load(any(), any())).thenReturn(new RepoConfig(Budgets.defaults(), List.of(), List.of("lead@corp.com"), List.of()));
        handler = new SlackActionsHandler(() -> SECRET, () -> store, () -> slack, () -> jira, () -> loader, () -> authorizer, () -> dispatcher,
                Clock.fixed(Instant.ofEpochSecond(NOW), ZoneOffset.UTC));
    }

    private APIGatewayV2HTTPEvent click(String user, String escalationId, String decision) {
        String value = "{\\\"ticketKey\\\":\\\"ABC-1\\\",\\\"escalationId\\\":\\\"" + escalationId + "\\\",\\\"decision\\\":\\\"" + decision + "\\\"}";
        String payload = "{\"type\":\"block_actions\",\"user\":{\"id\":\"" + user + "\"},\"channel\":{\"id\":\"C1\"},"
                + "\"message\":{\"ts\":\"1.1\",\"blocks\":[{\"type\":\"actions\",\"elements\":[]}]},"
                + "\"actions\":[{\"value\":\"" + value + "\"}]}";
        String body = "payload=" + URLEncoder.encode(payload, StandardCharsets.UTF_8);
        String ts = Long.toString(NOW);
        return APIGatewayV2HTTPEvent.builder().withBody(body).withHeaders(Map.of(
                "x-slack-request-timestamp", ts,
                "x-slack-signature", "v0=" + HmacVerifier.sign(SECRET, "v0:" + ts + ":" + body))).build();
    }

    @Test
    void unauthorisedUserGetsEphemeralRefusal() {
        when(authorizer.isAllowed(eq("U9"), anyString(), anyList())).thenReturn(false);
        assertThat(handler.handleRequest(click("U9", "e1", "RESUME"), null).getStatusCode()).isEqualTo(200);
        verify(slack).ephemeral("C1", "U9", "You're not allowed to resolve this escalation.");
        verify(dispatcher, never()).dispatch(any());
    }

    @Test
    void doubleClickDispatchesOnce() {
        when(authorizer.isAllowed(eq("U1"), anyString(), anyList())).thenReturn(true);
        when(store.claimEscalation("ABC-1", "e1", "U1")).thenReturn(true, false);
        when(store.string("ABC-1", "escalationResolvedBy")).thenReturn(Optional.of("U1"));
        handler.handleRequest(click("U1", "e1", "RESUME"), null);
        handler.handleRequest(click("U1", "e1", "RESUME"), null);
        verify(dispatcher, times(1)).dispatch(new RoutedSignal("ABC-1", WaitKind.ESCALATION_DECISION, "{\"decision\":\"RESUME\",\"by\":\"U1\"}"));
        verify(slack).ephemeral("C1", "U1", "Already handled by <@U1>.");
        verify(slack, times(1)).update(eq("C1"), eq("1.1"), anyList(), anyString());
    }

    @Test
    void staleEscalationIdIsAlreadyHandled() {
        when(authorizer.isAllowed(eq("U1"), anyString(), anyList())).thenReturn(true);
        when(store.claimEscalation("ABC-1", "old", "U1")).thenReturn(false);
        when(store.string("ABC-1", "escalationResolvedBy")).thenReturn(Optional.empty());
        handler.handleRequest(click("U1", "old", "ABORT"), null);
        verify(slack).ephemeral(eq("C1"), eq("U1"), startsWith("Already handled"));
        verify(dispatcher, never()).dispatch(any());
    }

    @Test
    void badSignatureIs401() {
        APIGatewayV2HTTPEvent e = click("U1", "e1", "RESUME");
        e.setHeaders(Map.of("x-slack-request-timestamp", Long.toString(NOW), "x-slack-signature", "v0=bad"));
        assertThat(handler.handleRequest(e, null).getStatusCode()).isEqualTo(401);
    }
}
