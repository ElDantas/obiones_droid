package io.agentic.memory;

import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

public final class PgVector {
    private static PostgreSQLContainer container;

    private PgVector() {
    }

    public static synchronized JdbcExecutor freshDatabase() {
        if (container == null) {
            container = new PostgreSQLContainer(DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));
            container.start();
        }
        String url = container.getJdbcUrl() + "&user=" + container.getUsername() + "&password=" + container.getPassword();
        if (!url.contains("?")) {
            url = container.getJdbcUrl() + "?user=" + container.getUsername() + "&password=" + container.getPassword();
        }
        JdbcExecutor sql = new JdbcExecutor(url);
        sql.execute("DROP TABLE IF EXISTS lessons");
        sql.execute("DROP TABLE IF EXISTS schema_version");
        Migrations.apply(sql);
        return sql;
    }
}
