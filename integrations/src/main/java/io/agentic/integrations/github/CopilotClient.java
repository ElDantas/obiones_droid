package io.agentic.integrations.github;

import com.fasterxml.jackson.databind.JsonNode;
import io.agentic.integrations.http.JsonHttp;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class CopilotClient {
    public static final String COPILOT_LOGIN = "copilot-swe-agent";

    private final GitHubApi api;

    public CopilotClient(JsonHttp http, String apiBase, Supplier<String> serviceUserToken) {
        this.api = new GitHubApi(http, apiBase, serviceUserToken);
    }

    public void assignCopilot(String repo, int issueNumber) {
        String[] parts = repo.split("/");
        JsonNode q = api.graphql("""
                query($owner:String!,$name:String!,$num:Int!){
                  repository(owner:$owner,name:$name){
                    issue(number:$num){ id }
                    suggestedActors(capabilities:[CAN_BE_ASSIGNED], first:100){
                      nodes{ login __typename ... on Bot { id } }
                    }
                  }
                }""", Map.of("owner", parts[0], "name", parts[1], "num", issueNumber));
        JsonNode repoNode = q.at("/data/repository");
        String issueId = repoNode.at("/issue/id").asText();
        String botId = null;
        for (JsonNode n : repoNode.at("/suggestedActors/nodes")) {
            if (COPILOT_LOGIN.equals(n.path("login").asText()) && n.hasNonNull("id")) {
                botId = n.get("id").asText();
            }
        }
        if (botId == null) {
            throw new CopilotUnavailableException(repo);
        }
        api.graphql("""
                mutation($a:ID!,$ids:[ID!]!){
                  replaceActorsForAssignable(input:{assignableId:$a, actorIds:$ids}){ assignable { ... on Issue { id } } }
                }""", Map.of("a", issueId, "ids", List.of(botId)));
    }

    public void instruct(String repo, int prNumber, String body) {
        String text = body.startsWith("@copilot") ? body : "@copilot " + body;
        api.http.post(api.url("/repos/" + repo + "/issues/" + prNumber + "/comments"), api.headers(), Map.of("body", text));
    }
}
