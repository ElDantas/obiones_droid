package io.agentic.functions.ops;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue;
import io.agentic.functions.config.Env;
import io.agentic.integrations.http.Json;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.firehose.FirehoseClient;
import software.amazon.awssdk.services.firehose.model.PutRecordBatchRequest;
import software.amazon.awssdk.services.firehose.model.Record;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LedgerExporter implements RequestHandler<DynamodbEvent, Map<String, Object>> {
    private final FirehoseClient firehose;
    private final String stream;

    public LedgerExporter() {
        this(FirehoseClient.create(), Env.get("LEDGER_FIREHOSE", "agentic-ledger"));
    }

    LedgerExporter(FirehoseClient firehose, String stream) {
        this.firehose = firehose;
        this.stream = stream;
    }

    @Override
    public Map<String, Object> handleRequest(DynamodbEvent event, Context context) {
        List<Record> records = new ArrayList<>();
        for (DynamodbEvent.DynamodbStreamRecord r : event.getRecords()) {
            if (r.getDynamodb() == null || r.getDynamodb().getNewImage() == null) {
                continue;
            }
            records.add(Record.builder().data(SdkBytes.fromUtf8String(toJsonLine(r.getDynamodb().getNewImage()))).build());
        }
        for (int i = 0; i < records.size(); i += 500) {
            firehose.putRecordBatch(PutRecordBatchRequest.builder().deliveryStreamName(stream)
                    .records(records.subList(i, Math.min(records.size(), i + 500))).build());
        }
        return Map.of("exported", records.size());
    }

    static String toJsonLine(Map<String, AttributeValue> image) {
        Map<String, Object> row = new LinkedHashMap<>();
        image.forEach((k, v) -> {
            if (v.getS() != null) {
                row.put(k.toLowerCase(), v.getS());
            } else if (v.getN() != null) {
                row.put(k.toLowerCase(), Double.parseDouble(v.getN()) % 1 == 0 ? (Object) Long.parseLong(v.getN()) : Double.parseDouble(v.getN()));
            } else if (v.getBOOL() != null) {
                row.put(k.toLowerCase(), v.getBOOL());
            }
        });
        try {
            return Json.MAPPER.writeValueAsString(row) + "\n";
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
