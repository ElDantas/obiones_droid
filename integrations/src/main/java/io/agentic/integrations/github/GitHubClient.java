package io.agentic.integrations.github;

import com.fasterxml.jackson.databind.JsonNode;
import io.agentic.core.scope.ScopeGuard.ChangedFile;
import io.agentic.integrations.github.model.Annotation;
import io.agentic.integrations.github.model.CheckRun;
import io.agentic.integrations.github.model.Commit;
import io.agentic.integrations.github.model.PullRequest;
import io.agentic.integrations.github.model.Review;
import io.agentic.integrations.github.model.ReviewComment;
import io.agentic.integrations.http.HttpFailure;
import io.agentic.integrations.http.JsonHttp;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

public class GitHubClient {
    private final GitHubApi api;

    public GitHubClient(JsonHttp http, String apiBase, Supplier<String> installationToken) {
        this.api = new GitHubApi(http, apiBase, installationToken);
    }

    public int createIssue(String repo, String title, String body) {
        JsonNode res = api.http.post(api.url("/repos/" + repo + "/issues"), api.headers(), Map.of("title", title, "body", body));
        return res.get("number").asInt();
    }

    public void comment(String repo, int number, String body) {
        api.http.post(api.url("/repos/" + repo + "/issues/" + number + "/comments"), api.headers(), Map.of("body", body));
    }

    public PullRequest getPullRequest(String repo, int number) {
        JsonNode p = api.http.get(api.url("/repos/" + repo + "/pulls/" + number), api.headers());
        return new PullRequest(
                p.path("number").asInt(),
                p.path("state").asText(),
                p.path("draft").asBoolean(),
                p.path("merged").asBoolean(),
                p.at("/head/sha").asText(),
                p.at("/head/ref").asText(),
                p.at("/user/login").asText(),
                p.path("html_url").asText(),
                p.path("additions").asInt(),
                p.path("deletions").asInt(),
                p.path("changed_files").asInt(),
                p.path("node_id").asText());
    }

    public List<ChangedFile> listFiles(String repo, int pr) {
        List<ChangedFile> files = new ArrayList<>();
        for (JsonNode f : api.http.getPaged(api.url("/repos/" + repo + "/pulls/" + pr + "/files?per_page=100"), api.headers())) {
            files.add(new ChangedFile(f.path("filename").asText(), f.path("additions").asInt(), f.path("deletions").asInt()));
        }
        return files;
    }

    public List<CheckRun> listCheckRuns(String repo, String sha) {
        JsonNode res = api.http.get(api.url("/repos/" + repo + "/commits/" + sha + "/check-runs?per_page=100"), api.headers());
        List<CheckRun> runs = new ArrayList<>();
        for (JsonNode c : res.path("check_runs")) {
            runs.add(new CheckRun(
                    c.path("id").asLong(),
                    c.path("name").asText(),
                    c.path("status").asText(),
                    c.path("conclusion").isNull() ? null : c.path("conclusion").asText(null),
                    c.at("/output/summary").asText(null),
                    c.at("/output/text").asText(null)));
        }
        return runs;
    }

    public List<Annotation> listCheckRunAnnotations(String repo, long checkRunId) {
        List<Annotation> annotations = new ArrayList<>();
        for (JsonNode a : api.http.getPaged(api.url("/repos/" + repo + "/check-runs/" + checkRunId + "/annotations?per_page=100"), api.headers())) {
            annotations.add(new Annotation(
                    a.path("path").asText(null),
                    a.hasNonNull("start_line") ? a.get("start_line").asInt() : null,
                    a.path("title").asText(""),
                    a.path("message").asText("")));
        }
        return annotations;
    }

    public List<ReviewComment> listReviewCommentsForReview(String repo, int pr, long reviewId) {
        List<ReviewComment> comments = new ArrayList<>();
        for (JsonNode c : api.http.getPaged(api.url("/repos/" + repo + "/pulls/" + pr + "/reviews/" + reviewId + "/comments?per_page=100"), api.headers())) {
            comments.add(toReviewComment(c));
        }
        return comments;
    }

    public List<ReviewComment> listReviewComments(String repo, int pr) {
        List<ReviewComment> comments = new ArrayList<>();
        for (JsonNode c : api.http.getPaged(api.url("/repos/" + repo + "/pulls/" + pr + "/comments?per_page=100"), api.headers())) {
            comments.add(toReviewComment(c));
        }
        return comments;
    }

    public List<Review> listReviews(String repo, int pr) {
        List<Review> reviews = new ArrayList<>();
        for (JsonNode r : api.http.getPaged(api.url("/repos/" + repo + "/pulls/" + pr + "/reviews?per_page=100"), api.headers())) {
            reviews.add(new Review(
                    r.path("id").asLong(),
                    r.at("/user/login").asText(),
                    r.path("state").asText(),
                    r.path("body").asText(""),
                    r.path("commit_id").asText(null)));
        }
        return reviews;
    }

    public List<Commit> listCommits(String repo, int pr) {
        List<Commit> commits = new ArrayList<>();
        for (JsonNode c : api.http.getPaged(api.url("/repos/" + repo + "/pulls/" + pr + "/commits?per_page=100"), api.headers())) {
            String sha = c.path("sha").asText();
            JsonNode detail = api.http.get(api.url("/repos/" + repo + "/commits/" + sha), api.headers());
            List<String> files = new ArrayList<>();
            for (JsonNode f : detail.path("files")) {
                files.add(f.path("filename").asText());
            }
            String author = c.at("/author/login").asText(c.at("/commit/author/name").asText());
            commits.add(new Commit(sha, author, c.at("/commit/message").asText(), files));
        }
        return commits;
    }

    public List<Integer> closingIssues(String repo, int pr) {
        String[] parts = repo.split("/");
        JsonNode res = api.graphql("""
                query($owner:String!,$name:String!,$num:Int!){
                  repository(owner:$owner,name:$name){
                    pullRequest(number:$num){ closingIssuesReferences(first:10){ nodes{ number } } }
                  }
                }""", Map.of("owner", parts[0], "name", parts[1], "num", pr));
        List<Integer> numbers = new ArrayList<>();
        for (JsonNode n : res.at("/data/repository/pullRequest/closingIssuesReferences/nodes")) {
            numbers.add(n.path("number").asInt());
        }
        return numbers;
    }

    public List<Integer> openPullRequestsForIssue(String repo, int issue) {
        String[] parts = repo.split("/");
        JsonNode res = api.graphql("""
                query($owner:String!,$name:String!,$num:Int!){
                  repository(owner:$owner,name:$name){
                    issue(number:$num){
                      timelineItems(first:100, itemTypes:[CONNECTED_EVENT, CROSS_REFERENCED_EVENT]){
                        nodes{
                          __typename
                          ... on ConnectedEvent { subject { ... on PullRequest { number state repository { nameWithOwner } } } }
                          ... on CrossReferencedEvent { source { ... on PullRequest { number state repository { nameWithOwner } } } }
                        }
                      }
                    }
                  }
                }""", Map.of("owner", parts[0], "name", parts[1], "num", issue));
        Set<Integer> numbers = new LinkedHashSet<>();
        for (JsonNode n : res.at("/data/repository/issue/timelineItems/nodes")) {
            JsonNode pr = n.has("subject") ? n.get("subject") : n.path("source");
            if (pr.has("number")
                    && "OPEN".equals(pr.path("state").asText())
                    && repo.equalsIgnoreCase(pr.at("/repository/nameWithOwner").asText())) {
                numbers.add(pr.get("number").asInt());
            }
        }
        return new ArrayList<>(numbers);
    }

    public List<Integer> findOpenPullRequestsByHead(String repo, String branch) {
        String owner = repo.split("/")[0];
        String url = api.url("/repos/" + repo + "/pulls?state=open&head=" + URLEncoder.encode(owner + ":" + branch, StandardCharsets.UTF_8));
        List<Integer> numbers = new ArrayList<>();
        for (JsonNode p : api.http.get(url, api.headers())) {
            numbers.add(p.path("number").asInt());
        }
        return numbers;
    }

    public String defaultBranch(String repo) {
        return api.http.get(api.url("/repos/" + repo), api.headers()).path("default_branch").asText("main");
    }

    public String branchSha(String repo, String branch) {
        return api.http.get(api.url("/repos/" + repo + "/git/ref/heads/" + branch), api.headers()).at("/object/sha").asText();
    }

    public void createBranch(String repo, String branch, String fromSha) {
        api.http.post(api.url("/repos/" + repo + "/git/refs"), api.headers(), Map.of("ref", "refs/heads/" + branch, "sha", fromSha));
    }

    public Optional<String> fileSha(String repo, String path, String ref) {
        try {
            JsonNode res = api.http.get(api.url("/repos/" + repo + "/contents/" + path + "?ref=" + URLEncoder.encode(ref, StandardCharsets.UTF_8)), api.headers());
            return Optional.ofNullable(res.path("sha").asText(null));
        } catch (HttpFailure e) {
            if (e.status() == 404) {
                return Optional.empty();
            }
            throw e;
        }
    }

    public void putFile(String repo, String path, String branch, String content, String message, String existingSha) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("message", message);
        body.put("content", Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8)));
        body.put("branch", branch);
        if (existingSha != null) {
            body.put("sha", existingSha);
        }
        api.http.put(api.url("/repos/" + repo + "/contents/" + path), api.headers(), body);
    }

    public int openPullRequest(String repo, String head, String base, String title, String body) {
        return api.http.post(api.url("/repos/" + repo + "/pulls"), api.headers(),
                Map.of("head", head, "base", base, "title", title, "body", body)).path("number").asInt();
    }

    public void addLabels(String repo, int number, List<String> labels) {
        api.http.post(api.url("/repos/" + repo + "/issues/" + number + "/labels"), api.headers(), Map.of("labels", labels));
    }

    public int countOpenPullRequestsMentioning(String repo, String text) {
        String q = URLEncoder.encode("repo:" + repo + " is:pr is:open \"" + text + "\"", StandardCharsets.UTF_8);
        return api.http.get(api.url("/search/issues?q=" + q), api.headers()).path("total_count").asInt();
    }

    public void markReadyForReview(String repo, int pr) {
        String nodeId = getPullRequest(repo, pr).nodeId();
        api.graphql("""
                mutation($id:ID!){ markPullRequestReadyForReview(input:{pullRequestId:$id}){ pullRequest { id } } }""",
                Map.of("id", nodeId));
    }

    public void requestReviewers(String repo, int pr, List<String> users) {
        if (users.isEmpty()) {
            return;
        }
        api.http.post(api.url("/repos/" + repo + "/pulls/" + pr + "/requested_reviewers"), api.headers(), Map.of("reviewers", users));
    }

    public void closePullRequest(String repo, int pr) {
        api.http.patch(api.url("/repos/" + repo + "/pulls/" + pr), api.headers(), Map.of("state", "closed"));
    }

    public Optional<String> readFile(String repo, String path) {
        return readFile(repo, path, null);
    }

    public Optional<String> readFile(String repo, String path, String ref) {
        String url = api.url("/repos/" + repo + "/contents/" + path)
                + (ref == null ? "" : "?ref=" + URLEncoder.encode(ref, StandardCharsets.UTF_8));
        try {
            JsonNode res = api.http.get(url, api.headers());
            String content = res.path("content").asText("").replaceAll("\\s", "");
            return Optional.of(new String(Base64.getDecoder().decode(content), StandardCharsets.UTF_8));
        } catch (HttpFailure e) {
            if (e.status() == 404) {
                return Optional.empty();
            }
            throw e;
        }
    }

    private static ReviewComment toReviewComment(JsonNode c) {
        return new ReviewComment(
                c.path("id").asLong(),
                c.at("/user/login").asText(),
                c.path("path").asText(null),
                c.hasNonNull("line") ? c.get("line").asInt() : null,
                c.path("body").asText(""),
                c.path("commit_id").asText(null),
                c.hasNonNull("pull_request_review_id") ? c.get("pull_request_review_id").asLong() : null);
    }
}
