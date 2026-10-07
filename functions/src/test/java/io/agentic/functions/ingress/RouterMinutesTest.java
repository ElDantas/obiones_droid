package io.agentic.functions.ingress;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RouterMinutesTest {

    @Test
    void roundsUpToWholeMinutes() {
        assertThat(GitHubEventRouter.minutes("2026-10-07T12:00:00Z", "2026-10-07T12:02:05Z")).isEqualTo(3);
        assertThat(GitHubEventRouter.minutes("2026-10-07T12:00:00Z", "2026-10-07T12:00:00Z")).isZero();
        assertThat(GitHubEventRouter.minutes(null, "2026-10-07T12:00:00Z")).isZero();
    }
}
