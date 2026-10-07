package io.agentic.memory;

import java.util.List;
import java.util.Map;

public interface SqlExecutor {
    List<Map<String, Object>> query(String sql, Map<String, Object> params);

    int update(String sql, Map<String, Object> params);

    void execute(String sql);
}
