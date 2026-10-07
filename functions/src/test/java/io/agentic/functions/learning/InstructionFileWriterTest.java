package io.agentic.functions.learning;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InstructionFileWriterTest {
    private final InstructionFileWriter writer = new InstructionFileWriter();

    @Test
    void newFileGetsFrontMatter() {
        String out = writer.append(null, "orders", "src/**", PromotionPolicyTest.lesson(3, 3, 0, Instant.now(), List.of()));
        assertThat(out).startsWith("---\napplyTo: \"src/**\"\n---\n\n# orders rules\n").contains("- **t:** l");
    }

    @Test
    void existingFileGetsBulletWithoutSecondHeader() {
        String existing = "---\napplyTo: \"src/**\"\n---\n\n# orders rules\n\n- **a:** b\n";
        String out = writer.append(existing, "orders", "src/**", PromotionPolicyTest.lesson(3, 3, 0, Instant.now(), List.of()));
        assertThat(out.split("applyTo").length).isEqualTo(2);
        assertThat(out).endsWith("- **a:** b\n\n- **t:** l\n");
    }

    @Test
    void areaAndApplyToComeFromComponentAndPaths() {
        assertThat(InstructionFileWriter.area(PromotionPolicyTest.lesson(3, 3, 0, Instant.now(), List.of()))).isEqualTo("orders");
        assertThat(InstructionFileWriter.applyTo(PromotionPolicyTest.lesson(3, 3, 0, Instant.now(), List.of()))).isEqualTo("src/**");
    }
}
