package io.agentic.functions.ingress;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HmacVerifierTest {
    private static final String SECRET = "It's a Secret to Everybody";
    private static final String BODY = "Hello, World!";
    private static final String EXPECTED = "sha256=757107ea0eb2509fc211221cce984b8a37570b6d7586c22c46f4379c8b043e17";

    @Test
    void validatesGitHubDocumentedExample() {
        assertThat(HmacVerifier.validGitHub(SECRET, BODY, EXPECTED)).isTrue();
    }

    @Test
    void tamperedBodyFails() {
        assertThat(HmacVerifier.validGitHub(SECRET, BODY + "!", EXPECTED)).isFalse();
    }

    @Test
    void missingHeaderFails() {
        assertThat(HmacVerifier.validGitHub(SECRET, BODY, null)).isFalse();
        assertThat(HmacVerifier.validGitHub(SECRET, BODY, "sha1=abc")).isFalse();
    }

    @Test
    void tokenComparison() {
        assertThat(HmacVerifier.validToken("a", "a")).isTrue();
        assertThat(HmacVerifier.validToken("a", "b")).isFalse();
        assertThat(HmacVerifier.validToken("a", null)).isFalse();
    }
}
