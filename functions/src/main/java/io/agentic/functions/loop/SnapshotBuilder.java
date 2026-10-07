package io.agentic.functions.loop;

import io.agentic.core.loop.FailureFingerprint;
import io.agentic.core.loop.IterationSnapshot;
import io.agentic.functions.gates.GateFinding;
import io.agentic.functions.gates.GateFindings;
import io.agentic.integrations.github.model.Commit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class SnapshotBuilder {

    public IterationSnapshot build(int iteration, GateFindings findings, List<Commit> commitsSincePrevious, Function<String, String> contentAtHead) {
        Set<String> failureIds = findings.blocking().stream().map(GateFinding::id).collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> failingFiles = findings.blocking().stream().map(GateFinding::file).filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> touched = commitsSincePrevious.stream().flatMap(c -> c.files().stream()).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, String> hashes = new LinkedHashMap<>();
        for (String path : touched) {
            String content = contentAtHead.apply(path);
            if (content != null) {
                hashes.put(path, sha256(content));
            }
        }
        return new IterationSnapshot(iteration, FailureFingerprint.of(failureIds), failureIds, failingFiles, touched, hashes);
    }

    public static List<Commit> commitsAfter(List<Commit> allCommits, String previousSha) {
        if (previousSha == null) {
            return allCommits;
        }
        for (int i = 0; i < allCommits.size(); i++) {
            if (allCommits.get(i).sha().equals(previousSha)) {
                return allCommits.subList(i + 1, allCommits.size());
            }
        }
        return allCommits;
    }

    static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
