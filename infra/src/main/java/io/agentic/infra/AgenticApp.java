package io.agentic.infra;

import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.Tags;
import software.amazon.awscdk.services.apigatewayv2.HttpMethod;

import java.util.Map;

public final class AgenticApp {
    private AgenticApp() {
    }

    public static void main(String[] args) {
        App app = new App();
        build(app);
        app.synth();
    }

    public static void build(App app) {
        Settings settings = Settings.from(app);
        StackProps props = StackProps.builder()
                .env(Environment.builder().account(settings.account()).region(settings.region()).build())
                .build();
        FoundationStack foundation = new FoundationStack(app, "AgenticFoundation", props, Map.of(), settings.local());
        Map<String, String> env = Map.of(
                "RUNS_TABLE", "agentic-runs",
                "LEDGER_TABLE", "agentic-ledger",
                "DELIVERIES_TABLE", "agentic-deliveries");
        if (!settings.local()) {
            ApiStack api = new ApiStack(app, "AgenticApi", props, env, settings);
            api.addDependency(foundation);
            Grants.common(api.route("JiraEvents", "io.agentic.functions.ingress.JiraEventHandler", HttpMethod.POST, "/jira/events").function());
            Grants.common(api.route("GitHubWebhook", "io.agentic.functions.ingress.GitHubWebhookHandler", HttpMethod.POST, "/github/webhook").function());
        }
        Tags.of(app).add("project", "agentic");
    }
}
