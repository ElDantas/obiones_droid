package io.agentic.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LessonRepositoryIT {
    private JdbcExecutor sql;
    private FakeEmbeddings embeddings;
    private LessonRepository repo;

    @BeforeEach
    void setUp() {
        sql = PgVector.freshDatabase();
        embeddings = new FakeEmbeddings()
                .with("Accessing the database from a service\nUse the repository class", FakeEmbeddings.axis(0, 1.0, 1, 0.0))
                .with("Database access in a service class\nUse the repository class only", FakeEmbeddings.axis(0, 0.95, 1, 0.31))
                .with("Formatting money\nUse BigDecimal", FakeEmbeddings.axis(2, 1.0, 3, 0.0));
        repo = new LessonRepository(sql, embeddings);
    }

    private static LessonDraft draft(String repo, String trigger, String lesson, String scope) {
        return new LessonDraft(repo, List.of("src/main/java/service"), "orders", "java", List.of("data-access"),
                "review_feedback", trigger, lesson, "https://github.com/acme/payments/pull/1", scope);
    }

    @Test
    void insertsNewLesson() {
        LessonRepository.WriteResult r = repo.upsert(draft("acme/payments", "Accessing the database from a service", "Use the repository class", "repo"));
        assertThat(r.created()).isTrue();
        LessonRecord rec = repo.get(r.id()).orElseThrow();
        assertThat(rec.hits()).isEqualTo(1);
        assertThat(rec.paths()).containsExactly("src/main/java/service");
        assertThat(rec.tags()).containsExactly("data-access");
        assertThat(rec.status()).isEqualTo("active");
        assertThat(rec.createdAt()).isNotNull();
    }

    @Test
    void nearDuplicateInSameRepoIncrementsHits() {
        String id = repo.upsert(draft("acme/payments", "Accessing the database from a service", "Use the repository class", "repo")).id();
        LessonRepository.WriteResult second = repo.upsert(draft("acme/payments", "Database access in a service class", "Use the repository class only", "repo"));
        assertThat(second.created()).isFalse();
        assertThat(second.id()).isEqualTo(id);
        LessonRecord rec = repo.get(id).orElseThrow();
        assertThat(rec.hits()).isEqualTo(2);
        assertThat(rec.evidence()).hasSize(2);
    }

    @Test
    void sameTextInAnotherRepoIsANewRow() {
        repo.upsert(draft("acme/payments", "Accessing the database from a service", "Use the repository class", "repo"));
        assertThat(repo.upsert(draft("acme/other", "Accessing the database from a service", "Use the repository class", "repo")).created()).isTrue();
    }

    @Test
    void secretsAndEmailsAreScrubbedBeforeStoring() {
        String id = repo.upsert(draft("acme/payments", "Token ghp_abcdefghijklmnopqrstuvwxyz0123456789 in config",
                "Ask jane.doe@corp.com before rotating", "repo")).id();
        LessonRecord rec = repo.get(id).orElseThrow();
        assertThat(rec.trigger()).contains("[REDACTED_GITHUB_TOKEN]").doesNotContain("ghp_");
        assertThat(rec.lesson()).contains("[REDACTED_EMAIL]").doesNotContain("jane.doe");
    }

    @Test
    void scoringStatusUsageAndTags() {
        String id = repo.upsert(draft("acme/payments", "Formatting money", "Use BigDecimal", "repo")).id();
        repo.scoreHelped(id);
        repo.scoreHelped(id);
        repo.scoreIgnored(id);
        repo.setStatus(id, "promoted");
        repo.addTag(id, "promotion-rejected");
        repo.addTag(id, "promotion-rejected");
        repo.markUsed(List.of(id), Instant.parse("2026-10-07T12:00:00Z"));
        LessonRecord rec = repo.get(id).orElseThrow();
        assertThat(rec.helped()).isEqualTo(2);
        assertThat(rec.ignored()).isEqualTo(1);
        assertThat(rec.status()).isEqualTo("promoted");
        assertThat(rec.tags()).containsExactly("data-access", "promotion-rejected");
        assertThat(rec.lastUsedAt()).isEqualTo(Instant.parse("2026-10-07T12:00:00Z"));
    }

    @Test
    void migrationsAreIdempotent() {
        assertThat(Migrations.apply(sql)).isEmpty();
    }
}
