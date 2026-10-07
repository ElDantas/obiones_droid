package io.agentic.memory;

import java.util.List;

public record SearchQuery(String text, String repo, List<String> paths, String component, int limit) {
    public SearchQuery {
        paths = paths == null ? List.of() : paths;
        limit = limit <= 0 ? 8 : limit;
    }
}
