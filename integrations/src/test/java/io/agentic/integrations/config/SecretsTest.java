package io.agentic.integrations.config;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SecretsTest {

    @Test
    void cachesWithinTtl() {
        SecretsManagerClient client = mock(SecretsManagerClient.class);
        when(client.getSecretValue(any(GetSecretValueRequest.class)))
                .thenReturn(GetSecretValueResponse.builder().secretString("{\"token\":\"t\"}").build());
        Secrets secrets = new Secrets(client, Clock.fixed(Instant.parse("2026-10-07T12:00:00Z"), ZoneOffset.UTC));
        assertThat(secrets.getJson("agentic/x").get("token").asText()).isEqualTo("t");
        secrets.get("agentic/x");
        verify(client, times(1)).getSecretValue(any(GetSecretValueRequest.class));
    }
}
