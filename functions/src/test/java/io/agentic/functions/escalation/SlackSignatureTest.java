package io.agentic.functions.escalation;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class SlackSignatureTest {
    private static final String SECRET = "8f742231b10e8888abcd99yyyzzz85a5";
    private static final String TS = "1531420618";
    private static final String BODY = "token=xyzz0WbapA4vBCDEFasx0q6G&team_id=T1DC2JH3J&team_domain=testteamnow&channel_id=G8PSS9T3V&channel_name=foobar&user_id=U2CERLKJA&user_name=roadrunner&command=%2Fwebhook-collect&text=&response_url=https%3A%2F%2Fhooks.slack.com%2Fcommands%2FT1DC2JH3J%2F397700885554%2F96rGlfmibIGlgcZRskXaIFfN&trigger_id=398738663015.47445629121.803a0bc887a14d10d2c447fce8b6703c";
    private static final String SIG = "v0=a2114d57b48eac39b9ad189dd8316235a7b4a8d21a10bd27519666489c69b503";

    @Test
    void validatesSlackDocumentedExample() {
        Clock at = Clock.fixed(Instant.ofEpochSecond(1531420618), ZoneOffset.UTC);
        assertThat(SlackSignature.valid(SECRET, TS, BODY, SIG, at)).isTrue();
    }

    @Test
    void rejectsOldTimestamp() {
        Clock later = Clock.fixed(Instant.ofEpochSecond(1531420618 + 301), ZoneOffset.UTC);
        assertThat(SlackSignature.valid(SECRET, TS, BODY, SIG, later)).isFalse();
    }

    @Test
    void rejectsTamperedBody() {
        Clock at = Clock.fixed(Instant.ofEpochSecond(1531420618), ZoneOffset.UTC);
        assertThat(SlackSignature.valid(SECRET, TS, BODY + "x", SIG, at)).isFalse();
    }
}
