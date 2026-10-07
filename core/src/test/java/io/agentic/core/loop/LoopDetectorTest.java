package io.agentic.core.loop;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class LoopDetectorTest {
    private final LoopDetector detector = new LoopDetector();

    @Test
    void repeatedFingerprintEscalates() {
        var a = new IterationSnapshot(1, "h1", Set.of("ci:OrderServiceTest.discount_rounding"), Set.of("src/Order.java"), Set.of("src/Order.java"), Map.of());
        var b = new IterationSnapshot(2, "h1", Set.of("ci:OrderServiceTest.discount_rounding"), Set.of("src/Order.java"), Set.of("src/Order.java"), Map.of());
        assertThat(detector.detect(List.of(a, b))).hasValueSatisfying(r -> assertThat(r).startsWith("Same failure repeated"));
    }

    @Test
    void touchedFilesDisjointFromPreviousFailuresIsNoProgress() {
        var a = new IterationSnapshot(1, "h1", Set.of("x"), Set.of("src/Order.java"), Set.of("src/Order.java"), Map.of());
        var b = new IterationSnapshot(2, "h2", Set.of("y"), Set.of("src/Other.java"), Set.of("README.md"), Map.of());
        assertThat(detector.detect(List.of(a, b))).hasValueSatisfying(r -> assertThat(r).startsWith("No progress"));
    }

    @Test
    void emptyTouchedFilesIsNoProgress() {
        var a = new IterationSnapshot(1, "h1", Set.of("x"), Set.of("src/Order.java"), Set.of("src/Order.java"), Map.of());
        var b = new IterationSnapshot(2, "h2", Set.of("y"), Set.of("src/Order.java"), Set.of(), Map.of());
        assertThat(detector.detect(List.of(a, b))).hasValueSatisfying(r -> assertThat(r).startsWith("No progress"));
    }

    @Test
    void oscillatingFileContentEscalates() {
        var a = new IterationSnapshot(1, "h1", Set.of("x"), Set.of("src/A.java"), Set.of("src/A.java"), Map.of("src/A.java", "x"));
        var b = new IterationSnapshot(2, "h2", Set.of("y"), Set.of("src/A.java"), Set.of("src/A.java"), Map.of("src/A.java", "y"));
        var c = new IterationSnapshot(3, "h3", Set.of("z"), Set.of("src/A.java"), Set.of("src/A.java"), Map.of("src/A.java", "x"));
        assertThat(detector.detect(List.of(a, b, c))).hasValue("Oscillating changes in src/A.java");
    }

    @Test
    void progressWithDifferentFailuresIsFine() {
        var a = new IterationSnapshot(1, "h1", Set.of("x"), Set.of("src/A.java"), Set.of("src/A.java"), Map.of("src/A.java", "1"));
        var b = new IterationSnapshot(2, "h2", Set.of("y"), Set.of("src/B.java"), Set.of("src/A.java"), Map.of("src/A.java", "2"));
        assertThat(detector.detect(List.of(a, b))).isEmpty();
    }

    @Test
    void singleSnapshotIsFine() {
        var a = new IterationSnapshot(1, "h1", Set.of("x"), Set.of("src/A.java"), Set.of(), Map.of());
        assertThat(detector.detect(List.of(a))).isEmpty();
    }
}
