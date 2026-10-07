package io.agentic.core.text;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

public final class Scrubber {
    private static final Map<Pattern, String> RULES = new LinkedHashMap<>();

    static {
        RULES.put(Pattern.compile("-----BEGIN [A-Z ]*PRIVATE KEY-----[\\s\\S]*?-----END [A-Z ]*PRIVATE KEY-----"), "[REDACTED_PRIVATE_KEY]");
        RULES.put(Pattern.compile("\\b(AKIA|ASIA)[0-9A-Z]{16}\\b"), "[REDACTED_AWS_KEY]");
        RULES.put(Pattern.compile("\\b(ghp|gho|ghu|ghs|ghr)_[A-Za-z0-9]{36,}\\b"), "[REDACTED_GITHUB_TOKEN]");
        RULES.put(Pattern.compile("\\bgithub_pat_[A-Za-z0-9_]{40,}\\b"), "[REDACTED_GITHUB_TOKEN]");
        RULES.put(Pattern.compile("\\bxox[abposr]-[A-Za-z0-9-]{10,}\\b"), "[REDACTED_SLACK_TOKEN]");
        RULES.put(Pattern.compile("\\beyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\b"), "[REDACTED_JWT]");
        RULES.put(Pattern.compile("(?i)\\b(password|passwd|secret|api[_-]?key|token)\\s*[:=]\\s*\\S+"), "$1=[REDACTED]");
        RULES.put(Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"), "[REDACTED_EMAIL]");
        RULES.put(Pattern.compile("\\b(?:\\d[ -]?){13,19}\\b"), "[REDACTED_NUMBER]");
    }

    private Scrubber() {
    }

    public static String scrub(String input) {
        if (input == null) {
            return null;
        }
        String out = input;
        for (Map.Entry<Pattern, String> rule : RULES.entrySet()) {
            out = rule.getKey().matcher(out).replaceAll(rule.getValue());
        }
        return out;
    }
}
