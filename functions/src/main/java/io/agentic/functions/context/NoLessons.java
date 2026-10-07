package io.agentic.functions.context;

import io.agentic.integrations.jira.JiraTicket;

import java.util.List;

public class NoLessons implements LessonProvider {
    @Override
    public List<Lesson> lessonsFor(JiraTicket ticket, String repo) {
        return List.of();
    }
}
