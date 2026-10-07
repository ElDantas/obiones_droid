package io.agentic.functions.memory;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import io.agentic.functions.config.Services;
import io.agentic.memory.Migrations;

import java.util.Map;

public class MigrateHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {
    @Override
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        return Map.of("applied", Migrations.apply(Services.instance().memorySql()));
    }
}
