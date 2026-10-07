package io.agentic.functions.digest;

import java.util.List;
import java.util.Map;

public record DigestData(String week, int started, int merged, int escalated, Map<String, Integer> escalationReasons,
                         Double medianLeadTimeHours, int premiumRequests, int actionsMinutes,
                         List<String> newLessons, List<String> promoted, List<String> expired) {
}
