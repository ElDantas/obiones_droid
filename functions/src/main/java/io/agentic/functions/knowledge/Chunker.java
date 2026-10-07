package io.agentic.functions.knowledge;

import java.util.ArrayList;
import java.util.List;

public final class Chunker {
    public static final int MAX_CHARS = 3200;
    public static final int OVERLAP = 300;

    private Chunker() {
    }

    public static List<String> chunk(String text, int maxChars, int overlap) {
        String t = text == null ? "" : text.strip();
        if (t.isEmpty()) {
            return List.of();
        }
        if (t.length() <= maxChars) {
            return List.of(t);
        }
        List<String> sections = new ArrayList<>();
        for (String block : t.split("\n(?=#+ |[A-Z][^\n]{0,80}\n)")) {
            sections.addAll(splitLong(block.strip(), maxChars));
        }
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String s : sections) {
            if (current.length() > 0 && current.length() + s.length() + 2 > maxChars) {
                chunks.add(current.toString().strip());
                String tail = current.length() > overlap ? current.substring(current.length() - overlap) : current.toString();
                current = new StringBuilder(tail).append("\n\n");
            }
            current.append(s).append("\n\n");
        }
        if (!current.toString().isBlank()) {
            chunks.add(current.toString().strip());
        }
        return chunks;
    }

    private static List<String> splitLong(String block, int maxChars) {
        if (block.length() <= maxChars) {
            return List.of(block);
        }
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String para : block.split("\n\n")) {
            if (current.length() + para.length() + 2 > maxChars && current.length() > 0) {
                parts.add(current.toString().strip());
                current = new StringBuilder();
            }
            if (para.length() > maxChars) {
                for (int i = 0; i < para.length(); i += maxChars) {
                    parts.add(para.substring(i, Math.min(para.length(), i + maxChars)));
                }
            } else {
                current.append(para).append("\n\n");
            }
        }
        if (!current.toString().isBlank()) {
            parts.add(current.toString().strip());
        }
        return parts;
    }
}
