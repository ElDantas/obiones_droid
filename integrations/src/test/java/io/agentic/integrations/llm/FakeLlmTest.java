package io.agentic.integrations.llm;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FakeLlmTest {
    private final FakeLlm fake = new FakeLlm();

    @Test
    void clarityDependsOnVagueMarker() {
        JsonNode ok = fake.converseJson("m", new Prompt("clarity", "s"), "Clear ticket", JsonNode.class);
        JsonNode vague = fake.converseJson("m", new Prompt("clarity", "s"), "VAGUE ticket", JsonNode.class);
        assertThat(ok.get("score").asInt()).isEqualTo(90);
        assertThat(vague.get("score").asInt()).isEqualTo(40);
    }

    @Test
    void embeddingsAreDeterministicNormalisedAndDistinct() {
        float[] a1 = fake.embed("alpha");
        float[] a2 = fake.embed("alpha");
        float[] b = fake.embed("beta");
        assertThat(a1).hasSize(Embeddings.DIMENSIONS).containsExactly(a2);
        double norm = 0;
        double dot = 0;
        for (int i = 0; i < a1.length; i++) {
            norm += a1[i] * a1[i];
            dot += a1[i] * b[i];
        }
        assertThat(norm).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-4));
        assertThat(dot).isLessThan(0.2);
    }

    @Test
    void lessonExtractRejectsNits() {
        JsonNode nit = fake.converseJson("m", new Prompt("lesson-extract", "s"), "nit: typo", JsonNode.class);
        JsonNode real = fake.converseJson("m", new Prompt("lesson-extract", "s"), "Use the repository layer. Raw SQL is banned.", JsonNode.class);
        assertThat(nit.get("generalisable").asBoolean()).isFalse();
        assertThat(real.get("lesson").asText()).isEqualTo("Use the repository layer");
    }
}
