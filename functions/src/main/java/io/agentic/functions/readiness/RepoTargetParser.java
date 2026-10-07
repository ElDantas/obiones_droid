package io.agentic.functions.readiness;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

public final class RepoTargetParser {
    private static final Pattern VALID = Pattern.compile("^[a-z0-9][a-z0-9-]*/[a-z0-9._-]+$");

    public record Result(Optional<String> repo, Optional<String> error) {
    }

    public Result parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return new Result(Optional.empty(), Optional.of("The \"Target repo\" field is empty; set it to owner/repo"));
        }
        String s = raw.trim()
                .replaceFirst("(?i)^https?://(www\\.)?github\\.com/", "")
                .replaceFirst("(?i)^git@github\\.com:", "")
                .replaceFirst("/+$", "")
                .replaceFirst("(?i)\\.git$", "")
                .toLowerCase(Locale.ROOT);
        if (!VALID.matcher(s).matches()) {
            return new Result(Optional.empty(), Optional.of("Target repo '" + raw.trim() + "' is not in owner/repo form"));
        }
        return new Result(Optional.of(s), Optional.empty());
    }
}
