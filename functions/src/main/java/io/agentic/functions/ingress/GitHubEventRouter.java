package io.agentic.functions.ingress;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import io.agentic.core.run.Actor;
import io.agentic.core.run.RunState;
import io.agentic.functions.readiness.RepoConfigLoader;
import io.agentic.functions.run.AbortService;
import io.agentic.functions.store.Run;
import io.agentic.functions.store.RunStore;
import io.agentic.functions.store.WaitKind;
import io.agentic.functions.usage.UsageMeter;
import io.agentic.integrations.github.GitHubClient;
import io.agentic.integrations.github.model.CheckRun;
import io.agentic.integrations.github.model.PullRequest;
import io.agentic.integrations.http.Json;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class GitHubEventRouter {
    private static final Set<RunState> CHECK_STATES = EnumSet.of(RunState.CODING, RunState.FIXING, RunState.HUMAN_FIX, RunState.GATES, RunState.ESCALATED);

    private final RunStore runStore;
    private final GitHubClient github;
    private final Identities identities;
    private final AbortService abortService;
    private final RepoConfigLoader configLoader;
    private final UsageMeter usage;
    private java.util.function.Supplier<io.agentic.memory.LessonRepository> lessons;

    public GitHubEventRouter withLessons(java.util.function.Supplier<io.agentic.memory.LessonRepository> lessons) {
        this.lessons = lessons;
        return this;
    }

    public GitHubEventRouter(RunStore runStore, GitHubClient github, Identities identities, AbortService abortService, RepoConfigLoader configLoader,
                             UsageMeter usage) {
        this.runStore = runStore;
        this.github = github;
        this.identities = identities;
        this.abortService = abortService;
        this.configLoader = configLoader;
        this.usage = usage;
    }

    public List<RoutedSignal> route(String eventName, JsonNode payload) {
        String action = payload.path("action").asText("");
        String repo = payload.at("/repository/full_name").asText("").toLowerCase(Locale.ROOT);
        if (repo.isEmpty()) {
            return List.of();
        }
        return switch (eventName + (action.isEmpty() ? "" : "." + action)) {
            case "pull_request.opened" -> prOpened(repo, payload);
            case "pull_request.review_requested" -> reviewRequested(repo, payload);
            case "pull_request.closed" -> prClosed(repo, payload);
            case "check_suite.completed" -> checksCompleted(repo, payload.at("/check_suite/head_sha").asText(), payload.at("/check_suite/pull_requests"));
            case "check_run.completed" -> checksCompleted(repo, payload.at("/check_run/head_sha").asText(), payload.at("/check_run/pull_requests"));
            case "pull_request_review.submitted" -> reviewSubmitted(repo, payload);
            case "workflow_run.completed" -> workflowRunCompleted(repo, payload.path("workflow_run"));
            default -> eventName.equals("push") ? push(repo, payload) : List.of();
        };
    }

    private List<RoutedSignal> prOpened(String repo, JsonNode payload) {
        JsonNode pr = payload.path("pull_request");
        if (!identities.isCopilot(pr.at("/user/login").asText())) {
            return List.of();
        }
        int number = pr.path("number").asInt();
        List<RoutedSignal> signals = new ArrayList<>();
        for (int issue : github.closingIssues(repo, number)) {
            Optional<Run> run = runStore.findByIssue(repo, issue);
            if (run.isEmpty() || run.get().state().isTerminal()) {
                continue;
            }
            Integer current = run.get().prNumber();
            if (current == null) {
                runStore.setPr(run.get().ticketKey(), number);
            } else if (current != number) {
                signals.add(signal(run.get().ticketKey(), WaitKind.PR_READY, Map.of("ambiguous", true, "prNumber", number)));
            }
        }
        return signals;
    }

    private List<RoutedSignal> reviewRequested(String repo, JsonNode payload) {
        JsonNode pr = payload.path("pull_request");
        if (!identities.isCopilot(pr.at("/user/login").asText())
                || !identities.isServiceUser(payload.at("/requested_reviewer/login").asText())) {
            return List.of();
        }
        int number = pr.path("number").asInt();
        Optional<Run> found = runForPr(repo, number);
        if (found.isEmpty()) {
            return List.of();
        }
        Run run = found.get();
        Map<String, Object> body = Map.of("prNumber", number, "headSha", pr.at("/head/sha").asText());
        RunState effective = run.state() == RunState.ESCALATED && run.escalatedFrom() != null ? run.escalatedFrom() : run.state();
        return switch (effective) {
            case CODING -> List.of(signal(run.ticketKey(), WaitKind.PR_READY, body));
            case FIXING, HUMAN_FIX -> List.of(signal(run.ticketKey(), WaitKind.PR_UPDATED, body));
            default -> List.of();
        };
    }

    private List<RoutedSignal> prClosed(String repo, JsonNode payload) {
        JsonNode pr = payload.path("pull_request");
        int number = pr.path("number").asInt();
        if (hasLabel(pr, io.agentic.functions.learning.PromotionJob.LABEL)) {
            promotionClosed(pr);
            return List.of();
        }
        Optional<Run> found = runStore.findByPr(repo, number);
        if (found.isEmpty() || found.get().state().isTerminal()) {
            return List.of();
        }
        Run run = found.get();
        boolean merged = pr.path("merged").asBoolean();
        if (run.state() == RunState.HUMAN_REVIEW) {
            return List.of(signal(run.ticketKey(), WaitKind.HUMAN_OUTCOME, Map.of("outcome", merged ? "MERGED" : "CLOSED")));
        }
        abortService.abort(run.ticketKey(), Actor.HUMAN, merged ? "PR merged outside the agent flow" : "PR closed manually");
        return List.of();
    }

    private static boolean hasLabel(JsonNode pr, String label) {
        for (JsonNode l : pr.path("labels")) {
            if (label.equals(l.path("name").asText())) {
                return true;
            }
        }
        return false;
    }

    private void promotionClosed(JsonNode pr) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("Lesson-Id: ([0-9a-f-]{36})").matcher(pr.path("body").asText(""));
        if (!m.find() || lessons == null) {
            return;
        }
        if (pr.path("merged").asBoolean()) {
            lessons.get().setStatus(m.group(1), "promoted");
        } else {
            lessons.get().addTag(m.group(1), io.agentic.functions.learning.PromotionPolicy.REJECTED_TAG);
        }
    }

    private List<RoutedSignal> checksCompleted(String repo, String sha, JsonNode pullRequests) {
        List<RoutedSignal> signals = new ArrayList<>();
        for (JsonNode p : pullRequests) {
            int number = p.path("number").asInt();
            Optional<Run> found = runStore.findByPr(repo, number);
            if (found.isEmpty() || !CHECK_STATES.contains(found.get().state())) {
                continue;
            }
            PullRequest pr = github.getPullRequest(repo, number);
            if (!sha.equals(pr.headSha())) {
                continue;
            }
            boolean gateAgents = configLoader.load(repo, found.get().budgets()).gateAgents();
            if (allChecksComplete(github.listCheckRuns(repo, sha), gateAgents)) {
                signals.add(signal(found.get().ticketKey(), WaitKind.CHECKS_COMPLETE, Map.of("headSha", sha)));
            }
        }
        return signals;
    }

    static final List<String> AGENT_CHECKS = List.of("agentic/ac-review", "agentic/qa");

    boolean allChecksComplete(List<CheckRun> runs, boolean gateAgents) {
        if (runs.isEmpty() || !runs.stream().allMatch(r -> "completed".equals(r.status()))) {
            return false;
        }
        return !gateAgents || AGENT_CHECKS.stream().allMatch(name -> runs.stream().anyMatch(r -> name.equals(r.name())));
    }

    private List<RoutedSignal> reviewSubmitted(String repo, JsonNode payload) {
        JsonNode review = payload.path("review");
        String reviewer = review.at("/user/login").asText();
        if (!identities.isHuman(reviewer)) {
            return List.of();
        }
        int number = payload.at("/pull_request/number").asInt();
        Optional<Run> found = runStore.findByPr(repo, number);
        if (found.isEmpty() || found.get().state() != RunState.HUMAN_REVIEW) {
            return List.of();
        }
        String state = review.path("state").asText("").toLowerCase(Locale.ROOT);
        long reviewId = review.path("id").asLong();
        boolean actionable = state.equals("changes_requested")
                || (state.equals("commented") && (!review.path("body").asText("").isBlank() || hasReviewComments(repo, number, reviewId)));
        if (!actionable) {
            return List.of();
        }
        return List.of(signal(found.get().ticketKey(), WaitKind.HUMAN_OUTCOME, Map.of("outcome", "CHANGES_REQUESTED", "reviewId", reviewId)));
    }

    private boolean hasReviewComments(String repo, int pr, long reviewId) {
        return github.listReviewComments(repo, pr).stream().anyMatch(c -> c.reviewId() != null && c.reviewId() == reviewId);
    }

    private List<RoutedSignal> workflowRunCompleted(String repo, JsonNode run) {
        int minutes = minutes(run.path("run_started_at").asText(null), run.path("updated_at").asText(null));
        for (JsonNode p : run.path("pull_requests")) {
            runStore.findByPr(repo, p.path("number").asInt())
                    .filter(r -> r.state().isActive())
                    .ifPresent(r -> usage.recordActionsMinutes(r.ticketKey(), run.path("id").asText(), minutes));
        }
        return List.of();
    }

    static int minutes(String startedAt, String updatedAt) {
        if (startedAt == null || updatedAt == null) {
            return 0;
        }
        long seconds = java.time.Duration.between(java.time.Instant.parse(startedAt), java.time.Instant.parse(updatedAt)).getSeconds();
        return seconds <= 0 ? 0 : (int) Math.ceil(seconds / 60.0);
    }

    private List<RoutedSignal> push(String repo, JsonNode payload) {
        String ref = payload.path("ref").asText("");
        if (!ref.startsWith("refs/heads/")) {
            return List.of();
        }
        String pusher = payload.at("/sender/login").asText(payload.at("/pusher/name").asText());
        if (!identities.isHuman(pusher)) {
            return List.of();
        }
        String branch = ref.substring("refs/heads/".length());
        Set<String> seen = new LinkedHashSet<>();
        for (int pr : github.findOpenPullRequestsByHead(repo, branch)) {
            runStore.findByPr(repo, pr)
                    .filter(r -> r.state().isActive())
                    .filter(r -> seen.add(r.ticketKey()))
                    .ifPresent(r -> runStore.setHumanOverride(r.ticketKey(), true));
        }
        return List.of();
    }

    private Optional<Run> runForPr(String repo, int number) {
        Optional<Run> byPr = runStore.findByPr(repo, number);
        if (byPr.isPresent()) {
            return byPr;
        }
        for (int issue : github.closingIssues(repo, number)) {
            Optional<Run> byIssue = runStore.findByIssue(repo, issue);
            if (byIssue.isPresent() && byIssue.get().prNumber() == null) {
                runStore.setPr(byIssue.get().ticketKey(), number);
                return runStore.get(byIssue.get().ticketKey());
            }
        }
        return Optional.empty();
    }

    private static RoutedSignal signal(String ticketKey, WaitKind kind, Map<String, Object> payload) {
        try {
            return new RoutedSignal(ticketKey, kind, Json.MAPPER.writeValueAsString(new LinkedHashMap<>(payload)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
