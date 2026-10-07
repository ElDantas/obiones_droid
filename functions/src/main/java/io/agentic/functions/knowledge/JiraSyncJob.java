package io.agentic.functions.knowledge;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.core.type.TypeReference;
import io.agentic.functions.config.Params;
import io.agentic.functions.config.Services;
import io.agentic.functions.readiness.RepoTargetParser;
import io.agentic.integrations.http.Json;
import io.agentic.integrations.jira.JiraClient;
import io.agentic.integrations.jira.JiraTicket;
import io.agentic.memory.LessonDraft;
import io.agentic.memory.LessonRepository;

import java.util.List;
import java.util.Map;

public class JiraSyncJob implements RequestHandler<Map<String, Object>, Map<String, Object>> {
    private final JiraClient jira;
    private final LessonRepository lessons;
    private final Params params;
    private final RepoTargetParser parser = new RepoTargetParser();

    public JiraSyncJob() {
        this(Services.instance().jira(), Services.instance().lessons(), Services.instance().params());
    }

    JiraSyncJob(JiraClient jira, LessonRepository lessons, Params params) {
        this.jira = jira;
        this.lessons = lessons;
        this.params = params;
    }

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        List<String> projects;
        try {
            projects = Json.MAPPER.readValue(params.find("/agentic/jira/syncProjects").orElse("[]"), new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            projects = List.of();
        }
        if (projects.isEmpty()) {
            return Map.of("tickets", 0);
        }
        String field = params.find("/agentic/jira/fields/targetRepo").orElse("customfield_10123");
        String jql = "project in (" + String.join(",", projects) + ") AND resolved >= -1d AND cf[" + field.replace("customfield_", "") + "] is not EMPTY";
        int synced = 0;
        for (String key : jira.searchKeys(jql)) {
            JiraTicket t = jira.getTicket(key);
            var repo = parser.parse(t.targetRepo()).repo();
            if (repo.isEmpty()) {
                continue;
            }
            String body = "Acceptance criteria:\n" + t.acceptanceCriteria() + "\n\nResolution: " + jira.resolution(key);
            lessons.upsertBySource("jira:" + key, new LessonDraft(repo.get(), List.of(), t.components().isEmpty() ? null : t.components().get(0),
                    null, List.of(), "spec", t.summary(), body, jira.browseUrl(key), "repo"));
            synced++;
        }
        return Map.of("tickets", synced);
    }
}
