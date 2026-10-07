package io.agentic.core.budget;

import java.util.List;

public record BudgetVerdict(Level level, List<String> reasons) {
    public enum Level { OK, WARN, BREACH }

    public boolean isBreach() {
        return level == Level.BREACH;
    }
}
