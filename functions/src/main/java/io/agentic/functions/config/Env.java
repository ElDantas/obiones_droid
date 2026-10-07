package io.agentic.functions.config;

import java.util.Optional;

public final class Env {
    private Env() {
    }

    public static Optional<String> find(String name) {
        String v = System.getProperty(name);
        if (v == null || v.isBlank()) {
            v = System.getenv(name);
        }
        return v == null || v.isBlank() ? Optional.empty() : Optional.of(v);
    }

    public static String get(String name, String fallback) {
        return find(name).orElse(fallback);
    }

    public static boolean fakeLlm() {
        return "fake".equalsIgnoreCase(get("AGENTIC_LLM_MODE", "bedrock"));
    }
}
