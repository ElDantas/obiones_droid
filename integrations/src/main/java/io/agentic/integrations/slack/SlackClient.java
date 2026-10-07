package io.agentic.integrations.slack;

import com.fasterxml.jackson.databind.JsonNode;
import io.agentic.integrations.http.JsonHttp;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SlackClient {
    private final JsonHttp http;
    private final String apiBase;
    private final String botToken;

    public SlackClient(JsonHttp http, String apiBase, String botToken) {
        this.http = http;
        this.apiBase = apiBase.endsWith("/") ? apiBase : apiBase + "/";
        this.botToken = botToken;
    }

    public String post(String channel, String threadTs, List<Map<String, Object>> blocks, String fallbackText) {
        Map<String, Object> body = new HashMap<>();
        body.put("channel", channel);
        body.put("text", fallbackText);
        if (blocks != null && !blocks.isEmpty()) {
            body.put("blocks", blocks);
        }
        if (threadTs != null) {
            body.put("thread_ts", threadTs);
        }
        return call("chat.postMessage", body).path("ts").asText();
    }

    public void update(String channel, String ts, List<Map<String, Object>> blocks, String fallbackText) {
        Map<String, Object> body = new HashMap<>();
        body.put("channel", channel);
        body.put("ts", ts);
        body.put("text", fallbackText);
        body.put("blocks", blocks == null ? List.of() : blocks);
        call("chat.update", body);
    }

    public void dmByEmail(String email, List<Map<String, Object>> blocks, String fallbackText) {
        String userId = lookupUserId(email);
        String channel = call("conversations.open", Map.of("users", userId)).at("/channel/id").asText();
        post(channel, null, blocks, fallbackText);
    }

    public void ephemeral(String channel, String userId, String text) {
        call("chat.postEphemeral", Map.of("channel", channel, "user", userId, "text", text));
    }

    public String lookupUserId(String email) {
        JsonNode res = http.get(apiBase + "users.lookupByEmail?email=" + URLEncoder.encode(email, StandardCharsets.UTF_8), headers());
        check("users.lookupByEmail", res);
        return res.at("/user/id").asText();
    }

    public String lookupEmail(String userId) {
        JsonNode res = http.get(apiBase + "users.info?user=" + URLEncoder.encode(userId, StandardCharsets.UTF_8), headers());
        check("users.info", res);
        return res.at("/user/profile/email").asText(null);
    }

    public java.util.Set<String> usergroupMemberIds(String handle) {
        JsonNode groups = http.get(apiBase + "usergroups.list", headers());
        check("usergroups.list", groups);
        String plain = handle.startsWith("@") ? handle.substring(1) : handle;
        for (JsonNode g : groups.path("usergroups")) {
            if (plain.equals(g.path("handle").asText())) {
                JsonNode users = http.get(apiBase + "usergroups.users.list?usergroup=" + g.path("id").asText(), headers());
                check("usergroups.users.list", users);
                java.util.Set<String> ids = new java.util.HashSet<>();
                users.path("users").forEach(u -> ids.add(u.asText()));
                return ids;
            }
        }
        return java.util.Set.of();
    }

    private JsonNode call(String method, Map<String, Object> body) {
        JsonNode res = http.post(apiBase + method, headers(), body);
        check(method, res);
        return res;
    }

    private static void check(String method, JsonNode res) {
        if (!res.path("ok").asBoolean(false)) {
            throw new SlackFailure(method, res.path("error").asText("unknown_error"));
        }
    }

    private Map<String, String> headers() {
        return Map.of("Authorization", "Bearer " + botToken, "Content-Type", "application/json; charset=utf-8");
    }
}
