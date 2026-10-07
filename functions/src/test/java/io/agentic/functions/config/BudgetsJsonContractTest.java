package io.agentic.functions.config;

import io.agentic.core.budget.Budgets;
import io.agentic.integrations.http.Json;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class BudgetsJsonContractTest {

    @Test
    void ssmDefaultBudgetsInFoundationStackMatchCoreDefaults() throws Exception {
        String source = Files.readString(Path.of("../infra/src/main/java/io/agentic/infra/FoundationStack.java"));
        Matcher m = Pattern.compile("DEFAULT_BUDGETS_JSON = (\"[^;]+);", Pattern.DOTALL).matcher(source);
        assertThat(m.find()).isTrue();
        String json = m.group(1).replaceAll("\"\\s*\\+\\s*\"", "").replaceAll("^\"|\"$", "").replace("\\\"", "\"");
        assertThat(Json.MAPPER.readValue(json, Budgets.class)).isEqualTo(Budgets.defaults());
    }
}
