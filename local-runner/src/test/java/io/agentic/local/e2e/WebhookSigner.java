package io.agentic.local.e2e;

import io.agentic.functions.ingress.HmacVerifier;

final class WebhookSigner {
    private WebhookSigner() {
    }

    static String sign(String body, String secret) {
        return "sha256=" + HmacVerifier.sign(secret, body);
    }
}
