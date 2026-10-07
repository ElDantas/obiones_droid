package io.agentic.local;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

class LocalApiServerIT {
    private LocalApiServer server;
    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void start() throws Exception {
        server = new LocalApiServer(0);
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop();
    }

    private HttpResponse<String> send(String method, String path, String body) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + server.port() + path));
        b.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void healthIsServedByTheRealHandler() throws Exception {
        HttpResponse<String> res = send("GET", "/health", null);
        assertThat(res.statusCode()).isEqualTo(200);
        assertThat(res.body()).isEqualTo("{\"ok\":true}");
    }

    @Test
    void unknownRouteIs404() throws Exception {
        assertThat(send("GET", "/nope", null).statusCode()).isEqualTo(404);
    }

    @Test
    void routesListEveryIngressPath() {
        assertThat(LocalApiServer.ROUTES).containsKeys("POST /jira/events", "POST /github/webhook", "POST /slack/actions", "POST /mcp");
    }
}
