package io.agentic.functions.memory;

import io.agentic.functions.context.Lesson;
import io.agentic.functions.context.LessonProvider;
import io.agentic.integrations.jira.JiraTicket;
import io.agentic.memory.HybridSearch;
import io.agentic.memory.LessonRecord;
import io.agentic.memory.LessonRepository;
import io.agentic.memory.SearchQuery;

import java.time.Clock;
import java.util.List;

public class MemoryLessonProvider implements LessonProvider {
    public static final int LIMIT = 8;

    private final HybridSearch search;
    private final LessonRepository repository;
    private final Clock clock;

    public MemoryLessonProvider(HybridSearch search, LessonRepository repository, Clock clock) {
        this.search = search;
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public List<Lesson> lessonsFor(JiraTicket ticket, String repo) {
        try {
            String component = ticket.components().isEmpty() ? null : ticket.components().get(0);
            List<LessonRecord> found = search.search(new SearchQuery(ticket.summary() + "\n" + ticket.acceptanceCriteria(), repo, List.of(), component, LIMIT));
            List<Lesson> lessons = found.stream().limit(LIMIT).map(r -> new Lesson(r.id(), r.trigger(), r.lesson(), r.evidence())).toList();
            repository.markUsed(lessons.stream().map(Lesson::id).toList(), clock.instant());
            return lessons;
        } catch (RuntimeException e) {
            System.err.println("WARN lesson retrieval failed for " + ticket.key() + ": " + e.getMessage());
            return List.of();
        }
    }
}
