package io.agentic.functions.learning;

import io.agentic.memory.LessonRecord;

public final class InstructionFileWriter {

    public String append(String existing, String area, String applyTo, LessonRecord l) {
        String header = "---\napplyTo: \"" + applyTo + "\"\n---\n\n# " + area + " rules\n";
        String base = (existing == null || existing.isBlank()) ? header : existing.stripTrailing() + "\n";
        return base + "\n- **" + l.trigger() + ":** " + l.lesson() + "\n";
    }

    public static String area(LessonRecord l) {
        if (l.component() != null && !l.component().isBlank()) {
            return slug(l.component());
        }
        if (!l.paths().isEmpty()) {
            return slug(l.paths().get(0).split("/")[0]);
        }
        return "general";
    }

    public static String applyTo(LessonRecord l) {
        if (l.paths().isEmpty()) {
            return "**";
        }
        return l.paths().get(0).split("/")[0] + "/**";
    }

    private static String slug(String s) {
        return s.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }
}
