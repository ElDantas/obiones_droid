package io.agentic.functions.context;

import io.agentic.integrations.jira.JiraTicket;

import java.util.List;

public interface LessonProvider {
    List<Lesson> lessonsFor(JiraTicket ticket, String repo);
}
