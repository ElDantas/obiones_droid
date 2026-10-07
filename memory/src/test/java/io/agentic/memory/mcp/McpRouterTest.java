package io.agentic.memory.mcp;

import io.agentic.memory.HybridSearch;
import io.agentic.memory.LessonDraft;
import io.agentic.memory.LessonRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class McpRouterTest {
    private final HybridSearch search = mock(HybridSearch.class);
    private final LessonRepository repository = mock(LessonRepository.class);
    private final McpRouter router = new McpRouter(new McpTools(search, repository));

    @Test
    void initializeEchoesSupportedVersion() {
        String res = router.handle("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-06-18\"}}", "acme/payments").orElseThrow();
        assertThat(res).contains("\"protocolVersion\":\"2025-06-18\"").contains("\"name\":\"agentic-memory\"").contains("\"tools\"");
    }

    @Test
    void unknownVersionGetsLatest() {
        assertThat(router.handle("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"1999-01-01\"}}", "r").orElseThrow())
                .contains("\"protocolVersion\":\"" + McpRouter.SUPPORTED_VERSIONS.get(0) + "\"");
    }

    @Test
    void listsBothTools() {
        assertThat(router.handle("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}", "r").orElseThrow())
                .contains("search_memory").contains("record_lesson").contains("inputSchema");
    }

    @Test
    void searchMemoryReturnsMarkdown() {
        when(search.search(any())).thenReturn(List.of());
        assertThat(router.handle("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\"search_memory\",\"arguments\":{\"query\":\"x\"}}}", "r").orElseThrow())
                .contains("No lessons found.");
    }

    @Test
    void recordLessonUsesAuthenticatedRepoNotInput() {
        when(repository.upsert(any())).thenReturn(new LessonRepository.WriteResult("id-1", true));
        router.handle("{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/call\",\"params\":{\"name\":\"record_lesson\",\"arguments\":"
                + "{\"trigger\":\"t\",\"lesson\":\"l\",\"repo\":\"evil/repo\"}}}", "acme/payments");
        verify(repository).upsert(argThat((LessonDraft d) -> d.repo().equals("acme/payments") && d.evidence().equals("mcp:acme/payments")));
    }

    @Test
    void unknownMethodIs32601() {
        assertThat(router.handle("{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"resources/list\"}", "r").orElseThrow()).contains("-32601");
    }

    @Test
    void notificationsHaveNoResponse() {
        assertThat(router.handle("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}", "r")).isEmpty();
    }

    @Test
    void unknownToolIsInvalidParams() {
        assertThat(router.handle("{\"jsonrpc\":\"2.0\",\"id\":6,\"method\":\"tools/call\",\"params\":{\"name\":\"rm_rf\",\"arguments\":{}}}", "r").orElseThrow())
                .contains("-32602");
    }
}
