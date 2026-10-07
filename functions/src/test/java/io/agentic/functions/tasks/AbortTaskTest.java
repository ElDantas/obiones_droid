package io.agentic.functions.tasks;

import io.agentic.core.run.Actor;
import io.agentic.functions.run.AbortService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AbortTaskTest {

    @Test
    void neverStopsItsOwnExecution() {
        AbortService service = mock(AbortService.class);
        new AbortTask(() -> service).handleRequest(Map.of("ticketKey", "ABC-1", "abortReason", "PR closed without merge"), null);
        verify(service).abort("ABC-1", Actor.BOT, "PR closed without merge", false);
    }
}
