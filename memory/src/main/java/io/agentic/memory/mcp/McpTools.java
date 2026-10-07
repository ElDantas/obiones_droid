package io.agentic.memory.mcp;

import io.agentic.memory.HybridSearch;
import io.agentic.memory.LessonDraft;
import io.agentic.memory.LessonRecord;
import io.agentic.memory.LessonRepository;
import io.agentic.memory.SearchQuery;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class McpTools {
    private final HybridSearch search;
    private final LessonRepository repository;

    public McpTools(HybridSearch search, LessonRepository repository) {
        this.search = search;
        this.repository = repository;
    }

    public List<Map<String, Object>> definitions() {
        return List.of(
                tool("search_memory", "Search lessons learned from past work in this repository: review feedback, fixed failures, conventions and ADRs. "
                                + "Call it before changing an unfamiliar area.",
                        Map.of("query", Map.of("type", "string", "description", "Short description of the change or problem"),
                                "paths", Map.of("type", "array", "items", Map.of("type", "string"), "description", "Files or folders you are changing"),
                                "limit", Map.of("type", "integer", "minimum", 1, "maximum", 10)),
                        List.of("query")),
                tool("record_lesson", "Record a reusable lesson (situation and rule) that future work in this repository should follow.",
                        Map.of("trigger", Map.of("type", "string", "description", "The situation in which the lesson applies"),
                                "lesson", Map.of("type", "string", "description", "The rule to follow, in the imperative"),
                                "paths", Map.of("type", "array", "items", Map.of("type", "string")),
                                "tags", Map.of("type", "array", "items", Map.of("type", "string"))),
                        List.of("trigger", "lesson")));
    }

    @SuppressWarnings("unchecked")
    public String call(String name, Map<String, Object> args, String repo) {
        return switch (name) {
            case "search_memory" -> {
                int limit = args.get("limit") instanceof Number n ? Math.max(1, Math.min(10, n.intValue())) : 5;
                List<String> paths = args.get("paths") instanceof List<?> l ? (List<String>) l : List.of();
                List<LessonRecord> found = search.search(new SearchQuery(String.valueOf(args.getOrDefault("query", "")), repo, paths, null, limit));
                if (found.isEmpty()) {
                    yield "No lessons found.";
                }
                StringBuilder sb = new StringBuilder();
                for (LessonRecord r : found) {
                    sb.append("- **").append(r.trigger()).append("** → ").append(r.lesson()).append('\n');
                }
                yield sb.toString().stripTrailing();
            }
            case "record_lesson" -> {
                List<String> paths = args.get("paths") instanceof List<?> l ? (List<String>) l : List.of();
                List<String> tags = args.get("tags") instanceof List<?> l ? (List<String>) l : List.of();
                LessonRepository.WriteResult r = repository.upsert(new LessonDraft(repo, paths, null, null, tags, "review_feedback",
                        String.valueOf(args.get("trigger")), String.valueOf(args.get("lesson")), "mcp:" + repo, "repo"));
                yield (r.created() ? "Recorded lesson " : "Matched existing lesson ") + r.id();
            }
            default -> throw new IllegalArgumentException("Unknown tool " + name);
        };
    }

    private static Map<String, Object> tool(String name, String description, Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", required);
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("name", name);
        t.put("description", description);
        t.put("inputSchema", schema);
        return t;
    }
}
