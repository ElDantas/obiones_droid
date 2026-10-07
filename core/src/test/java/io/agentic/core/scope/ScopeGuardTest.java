package io.agentic.core.scope;

import io.agentic.core.budget.Budgets;
import io.agentic.core.scope.ScopeGuard.ChangedFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScopeGuardTest {
    private final ScopeGuard guard = new ScopeGuard();
    private final Budgets budgets = Budgets.defaults();

    @Test
    void tooManyLinesIsAViolation() {
        List<String> v = guard.check(budgets, List.of(new ChangedFile("src/A.java", 700, 101)));
        assertThat(v).anyMatch(s -> s.startsWith("Diff has 801 lines"));
    }

    @Test
    void tooManyFilesIsAViolation() {
        List<ChangedFile> files = new ArrayList<>();
        for (int i = 0; i < 26; i++) {
            files.add(new ChangedFile("src/F" + i + ".java", 1, 0));
        }
        assertThat(guard.check(budgets, files)).anyMatch(s -> s.startsWith("Diff touches 26 files"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"db/migrations/V2__x.sql", "migrations/V1.sql", "infra/main.tf", ".github/workflows/ci.yml", "config/.env.local"})
    void forbiddenPathsAreViolations(String path) {
        List<String> v = guard.check(budgets, List.of(new ChangedFile(path, 1, 0)));
        assertThat(v).anyMatch(s -> s.startsWith("Forbidden path changed: " + path));
    }

    @Test
    void normalSourceFileIsFine() {
        assertThat(guard.check(budgets, List.of(new ChangedFile("src/main/App.java", 10, 2)))).isEmpty();
    }
}
