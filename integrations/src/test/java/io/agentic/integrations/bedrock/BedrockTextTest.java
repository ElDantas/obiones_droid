package io.agentic.integrations.bedrock;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseOutput;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.Message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BedrockTextTest {

    public record Clarity(int score) {
    }

    @Test
    void parsesFencedJson() {
        BedrockRuntimeClient client = mock(BedrockRuntimeClient.class);
        when(client.converse(any(ConverseRequest.class))).thenReturn(ConverseResponse.builder()
                .output(ConverseOutput.fromMessage(Message.builder().role(ConversationRole.ASSISTANT)
                        .content(ContentBlock.fromText("```json\n{\"score\": 82}\n```")).build()))
                .build());
        Clarity c = new BedrockText(client).converseJson("m", "sys", "user", Clarity.class);
        assertThat(c.score()).isEqualTo(82);
    }

    @Test
    void stripFenceLeavesPlainJson() {
        assertThat(BedrockText.stripFence("  {\"a\":1} ")).isEqualTo("{\"a\":1}");
    }
}
