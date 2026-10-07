package io.agentic.core.loop;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FailureFingerprintTest {

    @Test
    void orderInsensitive() {
        assertThat(FailureFingerprint.of(List.of("a", "b"))).isEqualTo(FailureFingerprint.of(List.of("b", "a")));
    }

    @Test
    void differentSetsDiffer() {
        assertThat(FailureFingerprint.of(List.of("a"))).isNotEqualTo(FailureFingerprint.of(List.of("b")));
    }

    @Test
    void emptyIsEmptyString() {
        assertThat(FailureFingerprint.of(List.of())).isEmpty();
    }
}
