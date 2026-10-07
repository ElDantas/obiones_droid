package io.agentic.functions.ops;

import io.agentic.functions.config.Params;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KillSwitchGuardTest {
    private final Params params = mock(Params.class);
    private final KillSwitchGuard guard = new KillSwitchGuard(params);

    @Test
    void globalOffPauses() {
        when(params.isGloballyEnabled()).thenReturn(false);
        assertThat(guard.check("acme/payments")).contains("Paused by kill switch (global)");
    }

    @Test
    void repoOffPauses() {
        when(params.isGloballyEnabled()).thenReturn(true);
        when(params.isEnabled("acme/payments")).thenReturn(false);
        assertThat(guard.check("acme/payments")).contains("Paused by kill switch (acme/payments)");
    }

    @Test
    void bothOnIsEmpty() {
        when(params.isGloballyEnabled()).thenReturn(true);
        when(params.isEnabled("acme/payments")).thenReturn(true);
        assertThat(guard.check("acme/payments")).isEmpty();
    }
}
