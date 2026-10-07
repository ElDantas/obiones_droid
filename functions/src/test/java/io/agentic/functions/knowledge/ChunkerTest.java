package io.agentic.functions.knowledge;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChunkerTest {

    @Test
    void shortTextIsOneChunk() {
        assertThat(Chunker.chunk("short text", 100, 10)).containsExactly("short text");
    }

    @Test
    void emptyTextHasNoChunks() {
        assertThat(Chunker.chunk("  ", 100, 10)).isEmpty();
    }

    @Test
    void longTextSplitsAtHeadingsWithOverlap() {
        String a = "# Context\n" + "a".repeat(80);
        String b = "# Decision\n" + "b".repeat(80);
        String c = "# Consequences\n" + "c".repeat(80);
        List<String> chunks = Chunker.chunk(a + "\n" + b + "\n" + c, 120, 20);
        assertThat(chunks.size()).isGreaterThan(1);
        assertThat(chunks.get(0)).startsWith("# Context");
        assertThat(chunks.get(1)).contains("# Decision");
        String tailOfFirst = chunks.get(0).substring(chunks.get(0).length() - 10);
        assertThat(chunks.get(1)).contains(tailOfFirst);
        assertThat(chunks).allMatch(ch -> ch.length() <= 120 + 20 + 2);
    }

    @Test
    void hugeParagraphIsHardSplit() {
        assertThat(Chunker.chunk("x".repeat(250), 100, 0)).hasSizeGreaterThanOrEqualTo(3);
    }
}
