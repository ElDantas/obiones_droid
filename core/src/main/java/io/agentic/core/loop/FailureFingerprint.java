package io.agentic.core.loop;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HexFormat;
import java.util.TreeSet;

public final class FailureFingerprint {
    private FailureFingerprint() {
    }

    public static String of(Collection<String> failureIds) {
        if (failureIds.isEmpty()) {
            return "";
        }
        String joined = String.join("\n", new TreeSet<>(failureIds));
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(joined.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
