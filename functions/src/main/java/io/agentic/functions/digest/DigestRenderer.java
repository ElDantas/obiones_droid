package io.agentic.functions.digest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class DigestRenderer {

    public String slackText(DigestData d) {
        if (d.started() == 0 && d.merged() == 0 && d.escalated() == 0 && d.newLessons().isEmpty()) {
            return "📊 *Agentic digest " + d.week() + "*\nNo agentic runs this week.";
        }
        StringBuilder sb = new StringBuilder("📊 *Agentic digest " + d.week() + "*\n");
        sb.append("• Runs started: ").append(d.started()).append(" · merged: ").append(d.merged()).append(" · escalated: ").append(d.escalated()).append('\n');
        sb.append("• Median lead time to PR ready: ").append(d.medianLeadTimeHours() == null ? "–" : String.format(Locale.ROOT, "%.1f h", d.medianLeadTimeHours())).append('\n');
        sb.append("• Premium requests: ").append(d.premiumRequests()).append(" · Actions minutes: ").append(d.actionsMinutes()).append('\n');
        if (!d.escalationReasons().isEmpty()) {
            sb.append("*Escalation reasons*\n");
            d.escalationReasons().forEach((r, n) -> sb.append("• ").append(r).append(": ").append(n).append('\n'));
        }
        section(sb, "New lessons", d.newLessons());
        section(sb, "Promoted to rules", d.promoted());
        section(sb, "Expired", d.expired());
        return sb.toString().stripTrailing();
    }

    public List<Map<String, Object>> slackBlocks(DigestData d) {
        List<Map<String, Object>> blocks = new ArrayList<>();
        blocks.add(Map.of("type", "section", "text", Map.of("type", "mrkdwn", "text", slackText(d))));
        return blocks;
    }

    public String confluenceStorage(DigestData d) {
        StringBuilder sb = new StringBuilder("<h2>Agentic digest " + esc(d.week()) + "</h2>");
        sb.append("<table><tbody>");
        row(sb, "Runs started", Integer.toString(d.started()));
        row(sb, "Merged", Integer.toString(d.merged()));
        row(sb, "Escalated", Integer.toString(d.escalated()));
        row(sb, "Median lead time to PR ready (h)", d.medianLeadTimeHours() == null ? "–" : String.format(Locale.ROOT, "%.1f", d.medianLeadTimeHours()));
        row(sb, "Premium requests", Integer.toString(d.premiumRequests()));
        row(sb, "Actions minutes", Integer.toString(d.actionsMinutes()));
        sb.append("</tbody></table>");
        if (!d.escalationReasons().isEmpty()) {
            sb.append("<h3>Escalation reasons</h3><ul>");
            d.escalationReasons().forEach((r, n) -> sb.append("<li>").append(esc(r)).append(": ").append(n).append("</li>"));
            sb.append("</ul>");
        }
        list(sb, "New lessons", d.newLessons());
        list(sb, "Promoted to rules", d.promoted());
        list(sb, "Expired", d.expired());
        return sb.toString();
    }

    private static void section(StringBuilder sb, String title, List<String> items) {
        if (items.isEmpty()) {
            return;
        }
        sb.append('*').append(title).append(" (").append(items.size()).append(")*\n");
        items.stream().limit(10).forEach(i -> sb.append("• ").append(i).append('\n'));
    }

    private static void list(StringBuilder sb, String title, List<String> items) {
        if (items.isEmpty()) {
            return;
        }
        sb.append("<h3>").append(esc(title)).append("</h3><ul>");
        items.forEach(i -> sb.append("<li>").append(esc(i)).append("</li>"));
        sb.append("</ul>");
    }

    private static void row(StringBuilder sb, String k, String v) {
        sb.append("<tr><th>").append(esc(k)).append("</th><td>").append(esc(v)).append("</td></tr>");
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
