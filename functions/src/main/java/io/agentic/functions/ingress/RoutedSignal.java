package io.agentic.functions.ingress;

import io.agentic.functions.store.WaitKind;

public record RoutedSignal(String ticketKey, WaitKind kind, String payloadJson) {
}
