package io.agentic.functions.ingress;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import io.agentic.functions.run.SignalDispatcher;
import io.agentic.functions.store.RunStore;
import io.agentic.functions.store.WaitKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GitHubWebhookHandlerTest {
    private static final String SECRET = "s3cret";
    private RunStore store;
    private GitHubEventRouter router;
    private SignalDispatcher dispatcher;
    private GitHubWebhookHandler handler;

    @BeforeEach
    void setUp() {
        store = mock(RunStore.class);
        router = mock(GitHubEventRouter.class);
        dispatcher = mock(SignalDispatcher.class);
        handler = new GitHubWebhookHandler(() -> SECRET, () -> store, () -> router, () -> dispatcher, Clock.systemUTC());
    }

    private APIGatewayV2HTTPEvent event(String body, String signature, String delivery) {
        return APIGatewayV2HTTPEvent.builder()
                .withHeaders(Map.of("X-Hub-Signature-256", signature, "X-GitHub-Delivery", delivery, "X-GitHub-Event", "pull_request"))
                .withBody(body)
                .build();
    }

    @Test
    void badSignatureIs401() {
        assertThat(handler.handleRequest(event("{}", "sha256=00", "d1"), null).getStatusCode()).isEqualTo(401);
    }

    @Test
    void duplicateDeliveryIsDispatchedOnce() {
        String body = "{\"action\":\"opened\"}";
        String sig = "sha256=" + HmacVerifier.sign(SECRET, body);
        RoutedSignal s = new RoutedSignal("ABC-1", WaitKind.PR_READY, "{}");
        when(router.route(eq("pull_request"), any())).thenReturn(List.of(s));
        when(store.markDelivery(eq("d1"), any())).thenReturn(true, false);

        assertThat(handler.handleRequest(event(body, sig, "d1"), null).getBody()).contains("\"signals\":1");
        assertThat(handler.handleRequest(event(body, sig, "d1"), null).getBody()).contains("duplicate");
        verify(dispatcher, times(1)).dispatch(s);
    }

    @Test
    void routerErrorsReturn200SoGitHubDoesNotRetryStorm() {
        String body = "{}";
        when(store.markDelivery(anyString(), any())).thenReturn(true);
        when(router.route(anyString(), any())).thenThrow(new IllegalStateException("boom"));
        var res = handler.handleRequest(event(body, "sha256=" + HmacVerifier.sign(SECRET, body), "d2"), null);
        assertThat(res.getStatusCode()).isEqualTo(200);
        assertThat(res.getBody()).contains("error");
        verify(dispatcher, never()).dispatch(any());
    }
}
