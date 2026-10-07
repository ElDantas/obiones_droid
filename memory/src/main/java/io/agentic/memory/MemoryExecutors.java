package io.agentic.memory;

import software.amazon.awssdk.services.rdsdata.RdsDataClient;

import java.util.function.Function;

public final class MemoryExecutors {
    private MemoryExecutors() {
    }

    public static SqlExecutor fromEnv(Function<String, String> env) {
        if ("jdbc".equalsIgnoreCase(env.apply("MEMORY_MODE"))) {
            return new JdbcExecutor(env.apply("MEMORY_JDBC_URL"));
        }
        return new DataApiExecutor(RdsDataClient.create(), env.apply("MEMORY_CLUSTER_ARN"), env.apply("MEMORY_SECRET_ARN"),
                env.apply("MEMORY_DB") == null ? "agentic" : env.apply("MEMORY_DB"));
    }
}
