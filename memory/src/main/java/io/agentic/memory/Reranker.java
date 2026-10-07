package io.agentic.memory;

import java.util.List;

public interface Reranker {
    List<Integer> rerank(String query, List<String> documents, int topN);

    static Reranker identity() {
        return (query, documents, topN) -> java.util.stream.IntStream.range(0, Math.min(topN, documents.size())).boxed().toList();
    }
}
