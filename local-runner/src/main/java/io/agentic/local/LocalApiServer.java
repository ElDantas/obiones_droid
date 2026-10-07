package io.agentic.local;

import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

public class LocalApiServer {
    public static final Map<String, String> ROUTES = new LinkedHashMap<>();

    static {
        ROUTES.put("GET /health", "io.agentic.functions.HealthHandler");
        ROUTES.put("POST /jira/events", "io.agentic.functions.ingress.JiraEventHandler");
        ROUTES.put("POST /github/webhook", "io.agentic.functions.ingress.GitHubWebhookHandler");
        ROUTES.put("POST /slack/actions", "io.agentic.functions.escalation.SlackActionsHandler");
        ROUTES.put("POST /mcp", "io.agentic.functions.memory.McpHandler");
    }

    private final HttpServer server;
    private final Map<String, RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse>> handlers = new ConcurrentHashMap<>();

    public LocalApiServer(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/", this::handle);
        server.setExecutor(Executors.newFixedThreadPool(8));
    }

    public static void main(String[] args) throws IOException {
        configureLocalAws();
        int port = Integer.parseInt(System.getProperty("PORT", System.getenv().getOrDefault("PORT", "8080")));
        LocalApiServer s = new LocalApiServer(port);
        s.start();
        System.out.println("LocalApiServer listening on http://localhost:" + s.port());
    }

    public static void configureLocalAws() {
        defaultProperty("aws.endpointUrl", "AWS_ENDPOINT_URL", "http://localhost:4566");
        defaultProperty("aws.region", "AWS_REGION", "eu-west-2");
        defaultProperty("aws.accessKeyId", "AWS_ACCESS_KEY_ID", "test");
        defaultProperty("aws.secretAccessKey", "AWS_SECRET_ACCESS_KEY", "test");
        defaultProperty("AGENTIC_LLM_MODE", "AGENTIC_LLM_MODE", "fake");
        defaultProperty("GITHUB_API_URL", "GITHUB_API_URL", "http://localhost:8089");
        defaultProperty("JIRA_BASE_URL", "JIRA_BASE_URL", "http://localhost:8089");
        defaultProperty("SLACK_API_URL", "SLACK_API_URL", "http://localhost:8089/api/");
        defaultProperty("CONFLUENCE_BASE_URL", "CONFLUENCE_BASE_URL", "http://localhost:8089");
        defaultProperty("MEMORY_MODE", "MEMORY_MODE", "jdbc");
        defaultProperty("MEMORY_JDBC_URL", "MEMORY_JDBC_URL", "jdbc:postgresql://localhost:55432/agentic?user=agentic&password=agentic");
    }

    private static void defaultProperty(String property, String envName, String fallback) {
        if (System.getProperty(property) == null) {
            System.setProperty(property, System.getenv().getOrDefault(envName, fallback));
        }
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(0);
    }

    public int port() {
        return server.getAddress().getPort();
    }

    private void handle(HttpExchange exchange) throws IOException {
        String route = exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath();
        String handlerClass = ROUTES.get(route);
        if (handlerClass == null) {
            ApiEvents.write(exchange, 404, "{\"error\":\"no route " + route + "\"}");
            return;
        }
        RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> handler;
        try {
            handler = handlers.computeIfAbsent(handlerClass, LocalApiServer::instantiate);
        } catch (IllegalStateException e) {
            ApiEvents.write(exchange, 501, "{\"error\":\"handler not implemented yet: " + handlerClass + "\"}");
            return;
        }
        try {
            ApiEvents.write(exchange, handler.handleRequest(ApiEvents.toEvent(exchange), null));
        } catch (RuntimeException e) {
            e.printStackTrace();
            ApiEvents.write(exchange, 500, "{\"error\":\"" + e.getClass().getSimpleName() + "\"}");
        }
    }

    @SuppressWarnings("unchecked")
    private static RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> instantiate(String className) {
        try {
            return (RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse>) Class.forName(className).getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(className, e);
        }
    }
}
