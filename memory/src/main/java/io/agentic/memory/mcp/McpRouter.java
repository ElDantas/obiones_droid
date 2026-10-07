package io.agentic.memory.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import io.agentic.integrations.http.Json;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class McpRouter {
    public static final List<String> SUPPORTED_VERSIONS = List.of("2025-11-25", "2025-06-18", "2025-03-26");

    private final McpTools tools;

    public McpRouter(McpTools tools) {
        this.tools = tools;
    }

    public Optional<String> handle(String body, String repo) {
        JsonNode req;
        try {
            req = Json.MAPPER.readTree(body);
        } catch (Exception e) {
            return Optional.of(error(null, -32700, "Parse error"));
        }
        JsonNode id = req.get("id");
        String method = req.path("method").asText("");
        if (id == null || id.isNull()) {
            return Optional.empty();
        }
        try {
            Object result = switch (method) {
                case "initialize" -> initialize(req.path("params"));
                case "ping" -> Map.of();
                case "tools/list" -> Map.of("tools", tools.definitions());
                case "tools/call" -> call(req.path("params"), repo);
                default -> null;
            };
            if (result == null) {
                return Optional.of(error(id, -32601, "Method not found: " + method));
            }
            return Optional.of(result(id, result));
        } catch (IllegalArgumentException e) {
            return Optional.of(error(id, -32602, e.getMessage()));
        } catch (RuntimeException e) {
            return Optional.of(result(id, Map.of("isError", true, "content", List.of(Map.of("type", "text", "text", "Tool failed: " + e.getMessage())))));
        }
    }

    private Map<String, Object> initialize(JsonNode params) {
        String requested = params.path("protocolVersion").asText("");
        String version = SUPPORTED_VERSIONS.contains(requested) ? requested : SUPPORTED_VERSIONS.get(0);
        return Map.of(
                "protocolVersion", version,
                "capabilities", Map.of("tools", Map.of()),
                "serverInfo", Map.of("name", "agentic-memory", "version", "0.1.0"));
    }

    private Map<String, Object> call(JsonNode params, String repo) {
        String name = params.path("name").asText();
        Map<String, Object> args = Json.MAPPER.convertValue(params.path("arguments"), new TypeReference<Map<String, Object>>() {
        });
        String text = tools.call(name, args == null ? Map.of() : args, repo);
        return Map.of("content", List.of(Map.of("type", "text", "text", text)), "isError", false);
    }

    private static String result(JsonNode id, Object result) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("jsonrpc", "2.0");
        m.put("id", id);
        m.put("result", result);
        return write(m);
    }

    private static String error(JsonNode id, int code, String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("jsonrpc", "2.0");
        m.put("id", id);
        m.put("error", Map.of("code", code, "message", message));
        return write(m);
    }

    private static String write(Object o) {
        try {
            return Json.MAPPER.writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
