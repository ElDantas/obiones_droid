package io.agentic.functions.ops;

import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.StreamRecord;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.firehose.FirehoseClient;
import software.amazon.awssdk.services.firehose.model.PutRecordBatchRequest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class LedgerExporterTest {

    @Test
    void writesLowercaseJsonLines() {
        String line = LedgerExporter.toJsonLine(Map.of(
                "ticketKey", new AttributeValue().withS("ABC-1"),
                "premiumRequests", new AttributeValue().withN("3")));
        assertThat(line).endsWith("\n").contains("\"ticketkey\":\"ABC-1\"").contains("\"premiumrequests\":3");
    }

    @Test
    void sendsNewImagesToFirehose() {
        FirehoseClient firehose = mock(FirehoseClient.class);
        DynamodbEvent.DynamodbStreamRecord r = new DynamodbEvent.DynamodbStreamRecord();
        r.setDynamodb(new StreamRecord().withNewImage(Map.of("to", new AttributeValue().withS("DONE"))));
        DynamodbEvent e = new DynamodbEvent();
        e.setRecords(List.of(r));
        assertThat(new LedgerExporter(firehose, "agentic-ledger").handleRequest(e, null)).containsEntry("exported", 1);
        verify(firehose).putRecordBatch(argThat((PutRecordBatchRequest req) -> req.deliveryStreamName().equals("agentic-ledger") && req.records().size() == 1));
    }
}
