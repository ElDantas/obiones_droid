package io.agentic.functions.knowledge;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.core.type.TypeReference;
import io.agentic.functions.config.Params;
import io.agentic.functions.config.Services;
import io.agentic.integrations.confluence.ConfluenceClient;
import io.agentic.integrations.http.Json;
import io.agentic.memory.LessonDraft;
import io.agentic.memory.LessonRepository;

import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

public class ConfluenceSyncJob implements RequestHandler<Map<String, Object>, Map<String, Object>> {
    static final DateTimeFormatter CQL_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC);

    private final ConfluenceClient confluence;
    private final LessonRepository lessons;
    private final Params params;
    private final BiConsumer<String, String> saveParam;
    private final Clock clock;

    public ConfluenceSyncJob() {
        this(Services.instance().confluence(), Services.instance().lessons(), Services.instance().params(),
                Services.instance()::putParam, Services.instance().clock());
    }

    ConfluenceSyncJob(ConfluenceClient confluence, LessonRepository lessons, Params params, BiConsumer<String, String> saveParam, Clock clock) {
        this.confluence = confluence;
        this.lessons = lessons;
        this.params = params;
        this.saveParam = saveParam;
        this.clock = clock;
    }

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        List<String> spaces = list("/agentic/confluence/syncSpaces", "[]");
        if (spaces.isEmpty()) {
            return Map.of("pages", 0, "chunks", 0);
        }
        List<String> labels = list("/agentic/confluence/syncLabels", "[\"adr\",\"spec\"]");
        String since = params.find("/agentic/confluence/lastSync").filter(v -> !v.isBlank()).orElse("1970-01-01 00:00");
        String startedAt = CQL_DATE.format(clock.instant());
        String cql = "space in (" + quoted(spaces) + ") AND label in (" + quoted(labels) + ") AND type = page AND lastmodified >= \"" + since + "\"";
        int chunks = 0;
        List<ConfluenceClient.Page> pages = confluence.searchPages(cql);
        for (ConfluenceClient.Page page : pages) {
            String kind = page.labels().contains("adr") ? "adr" : "spec";
            List<String> parts = Chunker.chunk(page.text(), Chunker.MAX_CHARS, Chunker.OVERLAP);
            for (int i = 0; i < parts.size(); i++) {
                String trigger = parts.size() == 1 ? page.title() : page.title() + " (part " + (i + 1) + ")";
                lessons.upsertBySource("confluence:" + page.id() + ":" + i,
                        new LessonDraft(null, List.of(), null, null, page.labels(), kind, trigger, parts.get(i), page.url(), "shared"));
                chunks++;
            }
        }
        saveParam.accept("/agentic/confluence/lastSync", startedAt);
        return Map.of("pages", pages.size(), "chunks", chunks);
    }

    private List<String> list(String name, String fallback) {
        try {
            return Json.MAPPER.readValue(params.find(name).orElse(fallback), new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    private static String quoted(List<String> values) {
        return String.join(",", values.stream().map(v -> "\"" + v.replace("\"", "") + "\"").toList());
    }
}
