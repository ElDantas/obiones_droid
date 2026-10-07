package io.agentic.functions.gates;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VerdictParserTest {

    @Test
    void parsesFencedVerdict() {
        String text = "```agentic-verdict\n{\"gate\":\"qa\",\"pass\":false,\"summary\":\"1 gap\",\"findings\":[{\"id\":\"QA-1\",\"file\":\"src/A.java\",\"line\":4,\"message\":\"Add a test\"}],\"premiumRequests\":1}\n```";
        assertThat(VerdictParser.parse(text)).hasValueSatisfying(v -> {
            assertThat(v.gate()).isEqualTo("qa");
            assertThat(v.pass()).isFalse();
            assertThat(v.findings()).singleElement().extracting(VerdictParser.Finding::id).isEqualTo("QA-1");
            assertThat(v.premiumRequests()).isEqualTo(1);
        });
    }

    @Test
    void noBlockIsEmpty() {
        assertThat(VerdictParser.parse("just text")).isEmpty();
        assertThat(VerdictParser.parse(null)).isEmpty();
    }

    @Test
    void invalidJsonIsEmpty() {
        assertThat(VerdictParser.parse("```agentic-verdict\n{not json}\n```")).isEmpty();
    }
}
