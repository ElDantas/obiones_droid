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
        Map<String, String> env = environment(settings);
        OrchestratorStack orchestrator = new OrchestratorStack(app, "AgenticOrchestrator", props, env, settings);
        orchestrator.addDependency(foundation);
        if (!settings.local()) {
            MemoryStack memory = new MemoryStack(app, "AgenticMemory", props, env, settings);
            memory.addDependency(foundation);
            orchestrator.addDependency(memory);
            ApiStack api = new ApiStack(app, "AgenticApi", props, env, settings);
            api.addDependency(foundation);
            api.addDependency(orchestrator);
            Grants.common(api.route("JiraEvents", "io.agentic.functions.ingress.JiraEventHandler", HttpMethod.POST, "/jira/events").function());
            Grants.common(api.route("GitHubWebhook", "io.agentic.functions.ingress.GitHubWebhookHandler", HttpMethod.POST, "/github/webhook").function());
            Grants.common(api.route("SlackActions", "io.agentic.functions.escalation.SlackActionsHandler", HttpMethod.POST, "/slack/actions").function());
        }
        Tags.of(app).add("project", "agentic");
    }

    static Map<String, String> environment(Settings settings) {
        Map<String, String> env = new java.util.LinkedHashMap<>();
        env.put("RUNS_TABLE", "agentic-runs");
        env.put("LEDGER_TABLE", "agentic-ledger");
        env.put("DELIVERIES_TABLE", "agentic-deliveries");
        if (settings.local()) {
            env.put("AGENTIC_LLM_MODE", "fake");
            env.put("GITHUB_API_URL", "http://host.docker.internal:8089");
            env.put("JIRA_BASE_URL", "http://host.docker.internal:8089");
            env.put("SLACK_API_URL", "http://host.docker.internal:8089/api/");
            env.put("CONFLUENCE_BASE_URL", "http://host.docker.internal:8089");
            env.put("MEMORY_MODE", "jdbc");
            env.put("MEMORY_JDBC_URL", "jdbc:postgresql://host.docker.internal:55432/agentic?user=agentic&password=agentic");
        }
        return env;
    }
}
