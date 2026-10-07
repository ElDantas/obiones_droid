package io.agentic.memory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class Migrations {
    public static final List<String> FILES = List.of("V1__lessons.sql", "V2__source_ref.sql");

    private Migrations() {
    }

    public static List<String> apply(SqlExecutor sql) {
        sql.execute("CREATE TABLE IF NOT EXISTS schema_version (name text PRIMARY KEY, applied_at timestamptz NOT NULL DEFAULT now())");
        List<String> applied = new ArrayList<>();
        for (String file : FILES) {
            if (!sql.query("SELECT name FROM schema_version WHERE name = :n", Map.of("n", file)).isEmpty()) {
                continue;
            }
            for (String statement : statements(read(file))) {
                sql.execute(statement);
            }
            sql.update("INSERT INTO schema_version (name) VALUES (:n)", Map.of("n", file));
            applied.add(file);
        }
        return applied;
    }

    static List<String> statements(String script) {
        List<String> out = new ArrayList<>();
        for (String part : script.split(";\\s*\\n")) {
            String s = part.strip();
            if (s.endsWith(";")) {
                s = s.substring(0, s.length() - 1);
            }
            if (!s.isEmpty()) {
                out.add(s);
            }
        }
        return out;
    }

    private static String read(String file) {
        try (InputStream in = Migrations.class.getResourceAsStream("/db/" + file)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
