package io.agentic.functions.loop;

import io.agentic.core.loop.IterationSnapshot;
import io.agentic.functions.gates.GateFinding;
import io.agentic.functions.gates.GateFindings;
import io.agentic.integrations.github.model.Commit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SnapshotBuilderTest {
    private final SnapshotBuilder builder = new SnapshotBuilder();

    @Test
    void failingFilesComeFromBlockingFindingsOnly() {
        GateFindings f = new GateFindings("sha",
                List.of(new GateFinding("ci", "ci:build:a", "src/A.java", 1, "x"), new GateFinding("ci", "ci:build", null, null, "y")),
                List.of(new GateFinding("copilot-review", "cr", "src/B.java", 2, "z")), List.of());
        IterationSnapshot s = builder.build(1, f, List.of(), p -> null);
        assertThat(s.failingFiles()).containsExactly("src/A.java");
        assertThat(s.failureIds()).containsExactly("ci:build:a", "ci:build");
        assertThat(s.failureFingerprint()).isNotEmpty();
    }

    @Test
    void touchedFilesComeFromCommitsAfterPreviousSha() {
        List<Commit> all = List.of(
                new Commit("c1", "Copilot", "first", List.of("src/A.java")),
                new Commit("c2", "Copilot", "second", List.of("src/B.java")),
                new Commit("c3", "Copilot", "third", List.of("src/C.java")));
        List<Commit> after = SnapshotBuilder.commitsAfter(all, "c1");
        IterationSnapshot s = builder.build(2, GateFindings.empty("c3"), after, p -> "content of " + p);
        assertThat(s.touchedFiles()).containsExactly("src/B.java", "src/C.java");
        assertThat(s.fileContentHashes()).containsKeys("src/B.java", "src/C.java");
    }

    @Test
    void unknownPreviousShaUsesAllCommitsAndDeletedFilesAreSkipped() {
        List<Commit> all = List.of(new Commit("c1", "Copilot", "m", List.of("gone.txt")));
        assertThat(SnapshotBuilder.commitsAfter(all, "zzz")).hasSize(1);
        assertThat(builder.build(1, GateFindings.empty("c1"), all, p -> null).fileContentHashes()).isEmpty();
    }
}
