package io.agentic.integrations.github;

import org.junit.jupiter.api.Test;

import java.security.KeyPair;

import static org.assertj.core.api.Assertions.assertThat;

class PemTest {

    @Test
    void readsPkcs8() {
        KeyPair kp = Keys.rsa();
        assertThat(Pem.readPrivateKey(Keys.pkcs8Pem(kp)).getEncoded()).isEqualTo(kp.getPrivate().getEncoded());
    }

    @Test
    void readsPkcs1AsIssuedByGitHub() {
        KeyPair kp = Keys.rsa();
        assertThat(Pem.readPrivateKey(Keys.pkcs1Pem(kp)).getEncoded()).isEqualTo(kp.getPrivate().getEncoded());
    }
}
