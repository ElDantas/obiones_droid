package io.agentic.functions.tasks;

import java.util.LinkedHashMap;
import java.util.Map;

public final class TaskSupport {
    private TaskSupport() {
    }

    public static String ticketKey(Map<String, Object> input) {
        Object key = input.get("ticketKey");
        if (!(key instanceof String s) || s.isBlank()) {
            throw new IllegalArgumentException("Task input has no ticketKey");
        }
        return s;
    }

    public static Map<String, Object> decision(String decision, String reason) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("decision", decision);
        out.put("reason", reason);
        return out;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> signal(Map<String, Object> input) {
        Object s = input.get("signal");
        return s instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    public static String string(Map<String, Object> input, String key, String fallback) {
        Object v = input.get(key);
        return v instanceof String s ? s : fallback;
    }
}
