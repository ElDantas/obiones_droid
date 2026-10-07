package io.agentic.core.loop;

import java.util.Map;
import java.util.Set;

public record IterationSnapshot(
        int iteration,
        String failureFingerprint,
        Set<String> failureIds,
        Set<String> failingFiles,
        Set<String> touchedFiles,
        Map<String, String> fileContentHashes) {
}
