package io.agentic.memory;

import com.fasterxml.jackson.core.type.TypeReference;
import io.agentic.integrations.http.Json;
import software.amazon.awssdk.services.rdsdata.RdsDataClient;
import software.amazon.awssdk.services.rdsdata.model.ExecuteStatementRequest;
import software.amazon.awssdk.services.rdsdata.model.ExecuteStatementResponse;
import software.amazon.awssdk.services.rdsdata.model.Field;
import software.amazon.awssdk.services.rdsdata.model.RecordsFormatType;
import software.amazon.awssdk.services.rdsdata.model.SqlParameter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DataApiExecutor implements SqlExecutor {
    private final RdsDataClient client;
    private final String clusterArn;
    private final String secretArn;
    private final String database;

    public DataApiExecutor(RdsDataClient client, String clusterArn, String secretArn, String database) {
        this.client = client;
        this.clusterArn = clusterArn;
        this.secretArn = secretArn;
        this.database = database;
    }

    @Override
    public List<Map<String, Object>> query(String sql, Map<String, Object> params) {
        ExecuteStatementResponse res = client.executeStatement(request(sql, params).formatRecordsAs(RecordsFormatType.JSON).build());
        if (res.formattedRecords() == null) {
            return List.of();
        }
        try {
            return Json.MAPPER.readValue(res.formattedRecords(), new TypeReference<List<Map<String, Object>>>() {
            });
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public int update(String sql, Map<String, Object> params) {
        return client.executeStatement(request(sql, params).build()).numberOfRecordsUpdated().intValue();
    }

    @Override
    public void execute(String sql) {
        client.executeStatement(request(sql, Map.of()).build());
    }

    private ExecuteStatementRequest.Builder request(String sql, Map<String, Object> params) {
        List<SqlParameter> parameters = new ArrayList<>();
        params.forEach((name, value) -> parameters.add(SqlParameter.builder().name(name).value(field(value)).build()));
        return ExecuteStatementRequest.builder()
                .resourceArn(clusterArn)
                .secretArn(secretArn)
                .database(database)
                .sql(sql)
                .parameters(parameters);
    }

    private static Field field(Object v) {
        if (v == null) {
            return Field.builder().isNull(true).build();
        }
        if (v instanceof Integer || v instanceof Long) {
            return Field.builder().longValue(((Number) v).longValue()).build();
        }
        if (v instanceof Double || v instanceof Float) {
            return Field.builder().doubleValue(((Number) v).doubleValue()).build();
        }
        if (v instanceof Boolean b) {
            return Field.builder().booleanValue(b).build();
        }
        return Field.builder().stringValue(v.toString()).build();
    }
}
