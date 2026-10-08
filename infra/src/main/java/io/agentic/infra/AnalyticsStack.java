package io.agentic.infra;

import software.amazon.awscdk.Duration;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.athena.CfnWorkGroup;
import software.amazon.awscdk.services.dynamodb.ITable;
import software.amazon.awscdk.services.glue.CfnDatabase;
import software.amazon.awscdk.services.glue.CfnTable;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.iam.ServicePrincipal;
import software.amazon.awscdk.services.kinesisfirehose.CfnDeliveryStream;
import software.amazon.awscdk.services.lambda.StartingPosition;
import software.amazon.awscdk.services.lambda.eventsources.DynamoEventSource;
import software.amazon.awscdk.services.s3.BlockPublicAccess;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.BucketEncryption;
import software.constructs.Construct;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AnalyticsStack extends Stack {
    public static final String STREAM_NAME = "agentic-ledger";
    public static final List<String[]> COLUMNS = List.of(
            new String[]{"ticketkey", "string"}, new String[]{"runid", "string"}, new String[]{"repo", "string"},
            new String[]{"from", "string"}, new String[]{"to", "string"}, new String[]{"ts", "string"}, new String[]{"week", "string"},
            new String[]{"actor", "string"}, new String[]{"iteration", "bigint"}, new String[]{"premiumrequests", "bigint"},
            new String[]{"actionsminutes", "bigint"}, new String[]{"reason", "string"});

    public AnalyticsStack(Construct scope, String id, StackProps props, ITable ledger, Map<String, String> env, Settings settings) {
        super(scope, id, props);
        Bucket bucket = Bucket.Builder.create(this, "Analytics")
                .bucketName("agentic-analytics-" + getAccount())
                .encryption(BucketEncryption.S3_MANAGED)
                .blockPublicAccess(BlockPublicAccess.BLOCK_ALL)
                .enforceSsl(true)
                .removalPolicy(RemovalPolicy.RETAIN)
                .build();

        Role firehoseRole = Role.Builder.create(this, "FirehoseRole").assumedBy(new ServicePrincipal("firehose.amazonaws.com")).build();
        bucket.grantReadWrite(firehoseRole);
        CfnDeliveryStream stream = CfnDeliveryStream.Builder.create(this, "LedgerStream")
                .deliveryStreamName(STREAM_NAME)
                .deliveryStreamType("DirectPut")
                .extendedS3DestinationConfiguration(CfnDeliveryStream.ExtendedS3DestinationConfigurationProperty.builder()
                        .bucketArn(bucket.getBucketArn())
                        .roleArn(firehoseRole.getRoleArn())
                        .prefix("ledger/dt=!{timestamp:yyyy-MM-dd}/")
                        .errorOutputPrefix("errors/!{firehose:error-output-type}/dt=!{timestamp:yyyy-MM-dd}/")
                        .bufferingHints(CfnDeliveryStream.BufferingHintsProperty.builder().intervalInSeconds(60).sizeInMBs(1).build())
                        .compressionFormat("GZIP")
                        .build())
                .build();

        Map<String, String> exporterEnv = new LinkedHashMap<>(env);
        exporterEnv.put("LEDGER_FIREHOSE", STREAM_NAME);
        JavaFunction exporter = new JavaFunction(this, "LedgerExporterFn", "io.agentic.functions.ops.LedgerExporter", exporterEnv, settings);
        exporter.function().addEventSource(DynamoEventSource.Builder.create(ledger)
                .startingPosition(StartingPosition.LATEST)
                .batchSize(100)
                .maxBatchingWindow(Duration.seconds(30))
                .retryAttempts(5)
                .build());
        exporter.function().addToRolePolicy(PolicyStatement.Builder.create()
                .actions(List.of("firehose:PutRecordBatch"))
                .resources(List.of(stream.getAttrArn()))
                .build());

        CfnDatabase glueDb = CfnDatabase.Builder.create(this, "GlueDb")
                .catalogId(getAccount())
                .databaseInput(CfnDatabase.DatabaseInputProperty.builder().name("agentic").build())
                .build();
        CfnTable table = CfnTable.Builder.create(this, "LedgerTable")
                .catalogId(getAccount())
                .databaseName("agentic")
                .tableInput(CfnTable.TableInputProperty.builder()
                        .name("ledger")
                        .tableType("EXTERNAL_TABLE")
                        .partitionKeys(List.of(CfnTable.ColumnProperty.builder().name("dt").type("string").build()))
                        .parameters(Map.of(
                                "classification", "json",
                                "projection.enabled", "true",
                                "projection.dt.type", "date",
                                "projection.dt.format", "yyyy-MM-dd",
                                "projection.dt.range", "2026-01-01,NOW",
                                "storage.location.template", "s3://" + bucket.getBucketName() + "/ledger/dt=${dt}/"))
                        .storageDescriptor(CfnTable.StorageDescriptorProperty.builder()
                                .location("s3://" + bucket.getBucketName() + "/ledger/")
                                .inputFormat("org.apache.hadoop.mapred.TextInputFormat")
                                .outputFormat("org.apache.hadoop.hive.ql.io.HiveIgnoreKeyTextOutputFormat")
                                .serdeInfo(CfnTable.SerdeInfoProperty.builder().serializationLibrary("org.openx.data.jsonserde.JsonSerDe").build())
                                .columns(COLUMNS.stream().map(c -> (Object) CfnTable.ColumnProperty.builder().name(c[0]).type(c[1]).build()).toList())
                                .build())
                        .build())
                .build();
        table.addDependency(glueDb);

        CfnWorkGroup.Builder.create(this, "Workgroup")
                .name("agentic")
                .workGroupConfiguration(CfnWorkGroup.WorkGroupConfigurationProperty.builder()
                        .resultConfiguration(CfnWorkGroup.ResultConfigurationProperty.builder()
                                .outputLocation("s3://" + bucket.getBucketName() + "/athena-results/").build())
                        .build())
                .build();
    }
}
