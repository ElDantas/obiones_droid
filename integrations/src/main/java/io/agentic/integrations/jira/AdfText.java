package io.agentic.integrations.jira;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Optional;
import java.util.regex.Pattern;

public final class AdfText {
    private AdfText() {
    }

    public static String flatten(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return "";
        }
        if (node.isTextual()) {
            return node.asText().strip();
        }
        StringBuilder sb = new StringBuilder();
        append(node, sb);
        return tidy(sb.toString());
    }

    public static Optional<String> section(JsonNode doc, Pattern heading) {
        if (doc == null || !doc.has("content")) {
            return Optional.empty();
        }
        StringBuilder sb = null;
        for (JsonNode block : doc.get("content")) {
            boolean isHeading = "heading".equals(block.path("type").asText());
            if (isHeading) {
                if (sb != null) {
                    break;
                }
                if (heading.matcher(flatten(block)).find()) {
                    sb = new StringBuilder();
                }
                continue;
            }
            if (sb != null) {
                append(block, sb);
            }
        }
        if (sb == null) {
            return Optional.empty();
        }
        String text = tidy(sb.toString());
        return text.isEmpty() ? Optional.empty() : Optional.of(text);
    }

    private static void append(JsonNode node, StringBuilder sb) {
        String type = node.path("type").asText();
        switch (type) {
            case "text" -> sb.append(node.path("text").asText());
            case "hardBreak" -> sb.append('\n');
            case "paragraph", "heading", "codeBlock", "blockquote" -> {
                children(node, sb);
                sb.append('\n');
            }
            case "listItem" -> {
                StringBuilder item = new StringBuilder();
                children(node, item);
                sb.append("- ").append(item.toString().strip()).append('\n');
            }
            default -> children(node, sb);
        }
    }

    private static void children(JsonNode node, StringBuilder sb) {
        for (JsonNode child : node.path("content")) {
            append(child, sb);
        }
    }

    private static String tidy(String s) {
        return s.replaceAll("\n{3,}", "\n\n").strip();
    }
}
