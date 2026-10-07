package io.agentic.memory;

import io.agentic.integrations.llm.Embeddings;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class HybridSearch {
    static final int CANDIDATES = 20;
    static final int RRF_K = 60;

    private final LessonRepository repository;
    private final Embeddings embeddings;
    private final Reranker reranker;

    public HybridSearch(LessonRepository repository, Embeddings embeddings, Reranker reranker) {
        this.repository = repository;
        this.embeddings = embeddings;
        this.reranker = reranker;
    }

    public List<LessonRecord> search(SearchQuery q) {
        String vector = LessonRepository.vector(embeddings.embed(q.text()));
        Map<String, Object> vp = new HashMap<>();
        vp.put("repo", q.repo());
        vp.put("e", vector);
        List<LessonRecord> byVector = repository.find("""
                status = 'active' AND (repo = CAST(:repo AS text) OR scope = 'shared')
                ORDER BY embedding <=> CAST(:e AS vector) LIMIT""" + " " + CANDIDATES, vp);
        Map<String, Object> kp = new HashMap<>();
        kp.put("repo", q.repo());
        kp.put("q", q.text());
        List<LessonRecord> byKeyword = repository.find("""
                status = 'active' AND (repo = CAST(:repo AS text) OR scope = 'shared')
                AND search_tsv @@ websearch_to_tsquery('english', :q)
                ORDER BY ts_rank_cd(search_tsv, websearch_to_tsquery('english', :q)) DESC LIMIT""" + " " + CANDIDATES, kp);

        Map<String, LessonRecord> union = new LinkedHashMap<>();
        Map<String, Double> score = new HashMap<>();
        rank(byVector, union, score);
        rank(byKeyword, union, score);
        for (LessonRecord r : union.values()) {
            double boost = 0;
            if (q.repo() != null && q.repo().equals(r.repo())) {
                boost += 0.02;
            }
            if (sharesPathPrefix(r.paths(), q.paths())) {
                boost += 0.01;
            }
            if (q.component() != null && q.component().equalsIgnoreCase(r.component())) {
                boost += 0.01;
            }
            score.merge(r.id(), boost, Double::sum);
        }
        List<LessonRecord> fused = new ArrayList<>(union.values());
        fused.sort(Comparator.comparingDouble((LessonRecord r) -> score.get(r.id())).reversed());
        List<LessonRecord> top = fused.subList(0, Math.min(CANDIDATES, fused.size()));
        try {
            List<String> docs = top.stream().map(r -> "trigger: " + r.trigger() + "\nlesson: " + r.lesson()).toList();
            List<Integer> order = reranker.rerank(q.text(), docs, q.limit());
            return order.stream().filter(i -> i >= 0 && i < top.size()).map(top::get).toList();
        } catch (RuntimeException e) {
            System.err.println("WARN rerank failed, using fused order: " + e.getMessage());
            return top.subList(0, Math.min(q.limit(), top.size()));
        }
    }

    private static void rank(List<LessonRecord> list, Map<String, LessonRecord> union, Map<String, Double> score) {
        for (int i = 0; i < list.size(); i++) {
            LessonRecord r = list.get(i);
            union.putIfAbsent(r.id(), r);
            score.merge(r.id(), 1.0 / (RRF_K + i + 1), Double::sum);
        }
    }

    static boolean sharesPathPrefix(List<String> lessonPaths, List<String> queryPaths) {
        for (String a : lessonPaths) {
            for (String b : queryPaths) {
                if (prefix(a).equals(prefix(b)) && !prefix(a).isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String prefix(String path) {
        String[] parts = path.split("/");
        return parts.length >= 2 ? parts[0] + "/" + parts[1] : parts[0];
    }
}
