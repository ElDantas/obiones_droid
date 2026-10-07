package io.agentic.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HybridSearchIT {
    private LessonRepository repo;
    private FakeEmbeddings embeddings;

    @BeforeEach
    void setUp() {
        embeddings = new FakeEmbeddings();
        repo = new LessonRepository(PgVector.freshDatabase(), embeddings);
    }

    private String add(String repoName, String trigger, String lesson, String scope) {
        return repo.upsert(new LessonDraft(repoName, List.of("src/main"), "orders", "java", List.of(), "review_feedback", trigger, lesson, "e", scope)).id();
    }

    @Test
    void keywordOnlyMatchStillAppears() {
        String id = add("acme/payments", "Applying voucher discounts", "Cap the combined discount at fifty percent", "repo");
        List<LessonRecord> found = new HybridSearch(repo, embeddings, Reranker.identity())
                .search(new SearchQuery("voucher discount cap", "acme/payments", List.of(), null, 8));
        assertThat(found).extracting(LessonRecord::id).contains(id);
    }

    @Test
    void otherReposPrivateLessonsNeverAppearButSharedDo() {
        String mine = add("acme/payments", "Mine", "Rule A", "repo");
        String theirs = add("acme/other", "Theirs", "Rule B", "repo");
        String shared = add(null, "Shared ADR", "Rule C", "shared");
        List<String> ids = new HybridSearch(repo, embeddings, Reranker.identity())
                .search(new SearchQuery("rule", "acme/payments", List.of(), null, 8)).stream().map(LessonRecord::id).toList();
        assertThat(ids).contains(mine, shared).doesNotContain(theirs);
    }

    @Test
    void promotedAndExpiredLessonsAreExcluded() {
        String promoted = add("acme/payments", "Promoted one", "Rule P", "repo");
        String expired = add("acme/payments", "Expired one", "Rule E", "repo");
        String active = add("acme/payments", "Active one", "Rule A", "repo");
        repo.setStatus(promoted, "promoted");
        repo.setStatus(expired, "expired");
        List<String> ids = new HybridSearch(repo, embeddings, Reranker.identity())
                .search(new SearchQuery("rule one", "acme/payments", List.of(), null, 8)).stream().map(LessonRecord::id).toList();
        assertThat(ids).contains(active).doesNotContain(promoted, expired);
    }

    @Test
    void rerankerFailureFallsBackToFusedOrder() {
        add("acme/payments", "Voucher stacking", "Cap at fifty percent", "repo");
        Reranker broken = (q, docs, n) -> {
            throw new IllegalStateException("throttled");
        };
        assertThat(new HybridSearch(repo, embeddings, broken).search(new SearchQuery("voucher", "acme/payments", List.of(), null, 8))).hasSize(1);
    }

    @Test
    void limitIsRespected() {
        for (int i = 0; i < 12; i++) {
            add("acme/payments", "Voucher rule " + i, "Do thing " + i, "repo");
        }
        assertThat(new HybridSearch(repo, embeddings, Reranker.identity()).search(new SearchQuery("voucher rule", "acme/payments", List.of(), null, 8))).hasSize(8);
    }

    @Test
    void pathPrefixMatchesFirstTwoSegments() {
        assertThat(HybridSearch.sharesPathPrefix(List.of("src/main/java/a"), List.of("src/main/kotlin"))).isTrue();
        assertThat(HybridSearch.sharesPathPrefix(List.of("src/test"), List.of("src/main"))).isFalse();
    }
}
