package io.agentic.integrations.http;

public class HttpFailure extends RuntimeException {
    private final int status;
    private final String body;

    public HttpFailure(int status, String method, String url, String body) {
        super(method + " " + url + " failed with " + status + ": " + abbreviate(body));
        this.status = status;
        this.body = body;
    }

    public int status() {
        return status;
    }

    public String body() {
        return body;
    }

    private static String abbreviate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 500 ? s.substring(0, 500) + "…" : s;
    }
}
