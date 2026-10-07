package io.agentic.functions.readiness;

import io.agentic.integrations.llm.Prompt;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class Prompts {
    private Prompts() {
    }

    public static Prompt load(String name) {
        try (InputStream in = Prompts.class.getResourceAsStream("/prompts/" + name + ".txt")) {
            if (in == null) {
                throw new IllegalStateException("Missing prompt " + name);
            }
            return new Prompt(name, new String(in.readAllBytes(), StandardCharsets.UTF_8).strip());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
