package io.agentic.integrations.jira;

import com.fasterxml.jackson.databind.JsonNode;
import io.agentic.integrations.http.Json;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class AdfTextTest {

    @Test
    void flattensParagraphsAndHardBreaks() throws Exception {
        JsonNode doc = Json.MAPPER.readTree("""
                {"type":"doc","content":[
                  {"type":"paragraph","content":[{"type":"text","text":"a"},{"type":"hardBreak"},{"type":"text","text":"b"}]},
                  {"type":"paragraph","content":[{"type":"text","text":"c"}]}]}""");
        assertThat(AdfText.flatten(doc)).isEqualTo("a\nb\nc");
    }

    @Test
    void sectionMissingReturnsEmpty() throws Exception {
        JsonNode doc = Json.MAPPER.readTree("{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"x\"}]}]}");
        assertThat(AdfText.section(doc, Pattern.compile("(?i)acceptance criteria"))).isEmpty();
    }

    @Test
    void plainStringFieldIsReturnedAsIs() {
        assertThat(AdfText.flatten(Json.MAPPER.getNodeFactory().textNode("  - a\n- b "))).isEqualTo("- a\n- b");
    }
}
