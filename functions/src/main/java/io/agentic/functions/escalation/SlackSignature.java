package io.agentic.functions.escalation;

import io.agentic.functions.ingress.HmacVerifier;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;

public final class SlackSignature {
    private SlackSignature() {
    }

    public static boolean valid(String signingSecret, String timestamp, String body, String signature, Clock clock) {
        if (timestamp == null || signature == null || body == null) {
            return false;
        }
        long ts;
        try {
            ts = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            return false;
        }
        if (Math.abs(clock.instant().getEpochSecond() - ts) > 300) {
            return false;
        }
        String expected = "v0=" + HmacVerifier.sign(signingSecret, "v0:" + timestamp + ":" + body);
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8));
    }
}
