package io.agentic.core.text;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScrubberTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "key AKIAABCDEFGHIJKLMNOP here|[REDACTED_AWS_KEY]",
            "token ghp_abcdefghijklmnopqrstuvwxyz0123456789 here|[REDACTED_GITHUB_TOKEN]",
            "pat github_pat_11ABCDEFG0123456789_abcdefghijklmnopqrstuvwxyz here|[REDACTED_GITHUB_TOKEN]",
            "slack xoxb-123456789012-abc here|[REDACTED_SLACK_TOKEN]",
            "jwt eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.dozjgNryP4J3jVmNHl0w5N_XgL0n3I9PlFUP0THsR8U here|[REDACTED_JWT]",
            "password=hunter2|password=[REDACTED]",
            "mail jane.doe@corp.com please|[REDACTED_EMAIL]",
            "card 4111 1111 1111 1111 ok|[REDACTED_NUMBER]"
    })
    void redactsSensitiveValues(String input, String marker) {
        String out = Scrubber.scrub(input);
        assertThat(out).contains(marker);
    }

    @Test
    void redactsPemBlocks() {
        String pem = "-----BEGIN RSA PRIVATE KEY-----\nMIIEow\nabc\n-----END RSA PRIVATE KEY-----";
        assertThat(Scrubber.scrub("x " + pem + " y")).isEqualTo("x [REDACTED_PRIVATE_KEY] y");
    }

    @Test
    void leavesCodeUnchanged() {
        String code = "int total = price * qty;";
        assertThat(Scrubber.scrub(code)).isEqualTo(code);
    }

    @Test
    void nullStaysNull() {
        assertThat(Scrubber.scrub(null)).isNull();
    }
}
