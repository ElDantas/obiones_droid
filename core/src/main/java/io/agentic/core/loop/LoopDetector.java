package io.agentic.core.loop;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

public final class LoopDetector {

    public Optional<String> detect(List<IterationSnapshot> history) {
        int n = history.size();
        if (n < 2) {
            return Optional.empty();
        }
        IterationSnapshot last = history.get(n - 1);
        IterationSnapshot prev = history.get(n - 2);

        if (!last.failureFingerprint().isEmpty() && last.failureFingerprint().equals(prev.failureFingerprint())) {
            return Optional.of("Same failure repeated: " + String.join(", ", new TreeSet<>(last.failureIds())));
        }

        if (!prev.failingFiles().isEmpty()
                && (last.touchedFiles().isEmpty() || Collections.disjoint(last.touchedFiles(), prev.failingFiles()))) {
            return Optional.of("No progress: last commit touched none of " + String.join(", ", new TreeSet<>(prev.failingFiles())));
        }

        if (n >= 3) {
            IterationSnapshot older = history.get(n - 3);
            for (Map.Entry<String, String> e : last.fileContentHashes().entrySet()) {
                String path = e.getKey();
                String now = e.getValue();
                String before = older.fileContentHashes().get(path);
                String middle = prev.fileContentHashes().get(path);
                if (now.equals(before) && middle != null && !now.equals(middle)) {
                    return Optional.of("Oscillating changes in " + path);
                }
            }
        }
        return Optional.empty();
    }
}
