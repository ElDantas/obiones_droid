package io.agentic.functions.digest;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DigestRendererTest {
    private final DigestRenderer renderer = new DigestRenderer();

    @Test
    void emptyWeek() {
        DigestData d = new DigestData("2026-W40", 0, 0, 0, Map.of(), null, 0, 0, List.of(), List.of(), List.of());
        assertThat(renderer.slackText(d)).contains("No agentic runs this week");
    }

    @Test
    void reasonsKeepCalculatorOrderAndHtmlIsEscaped() {
        Map<String, Integer> reasons = new LinkedHashMap<>();
        reasons.put("Same failure repeated", 3);
        reasons.put("Timed out", 1);
        DigestData d = new DigestData("2026-W40", 5, 2, 3, reasons, 4.5, 20, 100, List.of("Use <BigDecimal> → money"), List.of(), List.of());
        String text = renderer.slackText(d);
        assertThat(text.indexOf("Same failure repeated")).isLessThan(text.indexOf("Timed out"));
        assertThat(text).contains("4.5 h");
        assertThat(renderer.confluenceStorage(d)).contains("&lt;BigDecimal&gt;").doesNotContain("<BigDecimal>");
    }
}
