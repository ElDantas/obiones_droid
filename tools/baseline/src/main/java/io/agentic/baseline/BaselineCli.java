package io.agentic.baseline;

import io.agentic.integrations.http.JsonHttp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class BaselineCli {
    private BaselineCli() {
    }

    public static void main(String[] args) throws IOException {
        String jql = null;
        String out = "docs/baseline.md";
        List<String> repos = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--jql" -> jql = args[++i];
                case "--repo" -> repos.add(args[++i].toLowerCase());
                case "--out" -> out = args[++i];
                default -> throw new IllegalArgumentException("Unknown argument " + args[i]);
            }
        }
        if (jql == null || repos.isEmpty()) {
            System.err.println("usage: --jql '<JQL>' --repo owner/repo [--repo owner/repo2] [--out docs/baseline.md]");
            System.exit(2);
        }
        BaselineCollector collector = new BaselineCollector(
                new JsonHttp(),
                env("JIRA_BASE_URL"),
                env("JIRA_EMAIL"),
                env("JIRA_API_TOKEN"),
                System.getenv().getOrDefault("JIRA_STORY_POINTS_FIELD", "customfield_10016"),
                System.getenv().getOrDefault("GITHUB_API_URL", "https://api.github.com"),
                env("GITHUB_TOKEN"));
        List<TicketMetrics> tickets = collector.collect(jql, repos);
        Files.writeString(Path.of(out), new ReportWriter().write(jql, tickets, LocalDate.now()));
        System.out.println("Wrote " + out + " (" + tickets.size() + " tickets)");
    }

    private static String env(String name) {
        String v = System.getenv(name);
        if (v == null || v.isBlank()) {
            throw new IllegalStateException("Set " + name);
        }
        return v;
    }
}
