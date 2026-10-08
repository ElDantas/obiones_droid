package io.agentic.infra;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.assertions.Match;
import software.amazon.awscdk.assertions.Template;
import software.amazon.awscdk.services.apigatewayv2.HttpApi;
import software.amazon.awscdk.Stack;

import java.util.List;
import java.util.Map;

class ObservabilityStacksTest {
    private static Template analytics;
    private static Template alarms;

    @BeforeAll
    static void synth() throws Exception {
        App app = new App();
        StackProps props = StackProps.builder().env(Environment.builder().account("111111111111").region("eu-west-2").build()).build();
        Settings settings = ApiStackTest.settings(false);
        FoundationStack foundation = new FoundationStack(app, "F", props, Map.of());
        OrchestratorStack orchestrator = new OrchestratorStack(app, "O", props, Map.of(), settings);
        Stack apiHolder = new Stack(app, "A", props);
        HttpApi api = HttpApi.Builder.create(apiHolder, "Api").build();
        AnalyticsStack analyticsStack = new AnalyticsStack(app, "An", props, foundation.ledger(), Map.of(), settings);
        AlarmsStack alarmsStack = new AlarmsStack(app, "Al", props, orchestrator.machine(), api, Map.of(), settings);
        analytics = Template.fromStack(analyticsStack);
        alarms = Template.fromStack(alarmsStack);
    }

    @Test
    void firehoseWritesPartitionedGzipToS3() {
        analytics.hasResourceProperties("AWS::KinesisFirehose::DeliveryStream", Map.of(
                "DeliveryStreamName", "agentic-ledger",
                "ExtendedS3DestinationConfiguration", Match.objectLike(Map.of(
                        "Prefix", "ledger/dt=!{timestamp:yyyy-MM-dd}/",
                        "CompressionFormat", "GZIP"))));
    }

    @Test
    void exporterConsumesLedgerStream() {
        analytics.hasResourceProperties("AWS::Lambda::EventSourceMapping", Map.of("StartingPosition", "LATEST"));
        analytics.hasResourceProperties("AWS::Lambda::Function", Map.of("Handler", "io.agentic.functions.ops.LedgerExporter::handleRequest"));
    }

    @Test
    void glueTableUsesPartitionProjection() {
        analytics.hasResourceProperties("AWS::Glue::Table", Map.of("TableInput", Match.objectLike(Map.of(
                "Name", "ledger",
                "Parameters", Match.objectLike(Map.of("projection.enabled", "true", "projection.dt.type", "date"))))));
        analytics.hasResourceProperties("AWS::Athena::WorkGroup", Map.of("Name", "agentic"));
    }

    @Test
    void everyAlarmExistsAndNotifiesTheOpsTopic() {
        for (String name : List.of("ExecutionsFailed", "ExecutionsTimedOut", "Api5xx", "SignalDispatchErrors", "PremiumRequestsSpike", "LambdaErrors")) {
            alarms.hasResourceProperties("AWS::CloudWatch::Alarm", Map.of(
                    "AlarmName", "agentic-" + name,
                    "AlarmActions", Match.anyValue()));
        }
        alarms.hasResourceProperties("AWS::SNS::Subscription", Map.of("Protocol", "lambda"));
    }

    @Test
    void killSwitchChangesAreRouted() {
        alarms.hasResourceProperties("AWS::Events::Rule", Map.of("EventPattern", Match.objectLike(Map.of(
                "source", List.of("aws.ssm"),
                "detail-type", List.of("Parameter Store Change"),
                "detail", Map.of("name", List.of("/agentic/enabled", Map.of("prefix", "/agentic/repos/")))))));
    }
}
