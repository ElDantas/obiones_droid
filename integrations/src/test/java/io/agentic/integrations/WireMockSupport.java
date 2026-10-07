package io.agentic.integrations;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.agentic.integrations.http.JsonHttp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

public abstract class WireMockSupport {
    protected WireMockServer wm;
    protected final List<Duration> sleeps = new ArrayList<>();
    protected JsonHttp http;

    @BeforeEach
    void startWireMock() {
        wm = new WireMockServer(options().dynamicPort());
        wm.start();
        http = new JsonHttp(HttpClient.newHttpClient(), sleeps::add);
    }

    @AfterEach
    void stopWireMock() {
        wm.stop();
    }

    protected String base() {
        return wm.baseUrl();
    }
}
