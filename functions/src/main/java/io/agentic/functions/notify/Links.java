package io.agentic.functions.notify;

public record Links(String githubWeb, String jiraBrowse) {
    public String issue(String repo, Integer number) {
        return number == null ? "(no issue)" : githubWeb + "/" + repo + "/issues/" + number;
    }

    public String pr(String repo, Integer number) {
        return number == null ? "(no PR)" : githubWeb + "/" + repo + "/pull/" + number;
    }

    public String ticket(String key) {
        return jiraBrowse + "/browse/" + key;
    }
}
