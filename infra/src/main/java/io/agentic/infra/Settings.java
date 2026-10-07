package io.agentic.infra;

import software.constructs.Construct;

public record Settings(String account, String region, boolean local, String functionsJar) {
    public static Settings from(Construct scope) {
        return new Settings(
                ctx(scope, "agentic:account", "000000000000"),
                ctx(scope, "agentic:region", "eu-west-2"),
                Boolean.parseBoolean(ctx(scope, "agentic:local", "false")),
                ctx(scope, "agentic:functionsJar", "../functions/target/functions.jar"));
    }

    private static String ctx(Construct scope, String key, String fallback) {
        Object v = scope.getNode().tryGetContext(key);
        return v == null ? fallback : v.toString();
    }
}
