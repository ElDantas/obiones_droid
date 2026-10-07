package io.agentic.infra;

import org.junit.jupiter.api.Test;
import software.amazon.awscdk.App;
import software.amazon.awscdk.AppProps;
import software.amazon.awscdk.assertions.Template;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

class ApiStackTest {

    static Settings settings(boolean local) throws IOException {
        Path jar = Files.createTempFile("functions", ".jar");
        return new Settings("000000000000", "eu-west-2", local, jar.toString());
    }

    @Test
    void healthRouteWithSnapStartAndOutput() throws IOException {
        App app = new App(AppProps.builder().build());
        Template t = Template.fromStack(new ApiStack(app, "A", null, Map.of("X", "1"), settings(false)));
        t.hasResourceProperties("AWS::ApiGatewayV2::Route", Map.of("RouteKey", "GET /health"));
        t.hasResourceProperties("AWS::Lambda::Function", Map.of(
                "Runtime", "java21",
                "Handler", "io.agentic.functions.HealthHandler::handleRequest",
                "SnapStart", Map.of("ApplyOn", "PublishedVersions")));
        t.resourceCountIs("AWS::Lambda::Alias", 1);
        t.hasOutput("ApiUrl", Map.of());
    }

    @Test
    void localModeHasNoSnapStartOrAlias() throws IOException {
        App app = new App();
        Template t = Template.fromStack(new ApiStack(app, "A", null, Map.of(), settings(true)));
        t.resourceCountIs("AWS::Lambda::Alias", 0);
    }
}
