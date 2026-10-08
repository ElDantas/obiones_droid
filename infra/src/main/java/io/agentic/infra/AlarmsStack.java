package io.agentic.infra;

import software.amazon.awscdk.Duration;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.apigatewayv2.HttpApi;
import software.amazon.awscdk.services.cloudwatch.Alarm;
import software.amazon.awscdk.services.cloudwatch.ComparisonOperator;
import software.amazon.awscdk.services.cloudwatch.IMetric;
import software.amazon.awscdk.services.cloudwatch.Metric;
import software.amazon.awscdk.services.cloudwatch.MetricOptions;
import software.amazon.awscdk.services.cloudwatch.TreatMissingData;
import software.amazon.awscdk.services.cloudwatch.actions.SnsAction;
import software.amazon.awscdk.services.events.EventPattern;
import software.amazon.awscdk.services.events.Match;
import software.amazon.awscdk.services.events.Rule;
import software.amazon.awscdk.services.events.targets.SnsTopic;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.sns.Topic;
import software.amazon.awscdk.services.sns.subscriptions.LambdaSubscription;
import software.amazon.awscdk.services.stepfunctions.StateMachine;
import software.constructs.Construct;

import java.util.List;
import java.util.Map;

public class AlarmsStack extends Stack {
    private final Topic topic;

    public AlarmsStack(Construct scope, String id, StackProps props, StateMachine machine, HttpApi api, Map<String, String> env, Settings settings) {
        super(scope, id, props);
        topic = Topic.Builder.create(this, "Ops").topicName("agentic-ops").build();
        JavaFunction handler = new JavaFunction(this, "OpsAlertFn", "io.agentic.functions.ops.OpsAlertHandler", env, settings);
        Grants.common(handler.function());
        topic.addSubscription(new LambdaSubscription(handler.target()));

        alarm("ExecutionsFailed", machine.metricFailed(MetricOptions.builder().period(Duration.minutes(5)).statistic("Sum").build()), 1);
        alarm("ExecutionsTimedOut", machine.metricTimedOut(MetricOptions.builder().period(Duration.hours(1)).statistic("Sum").build()), 1);
        alarm("Api5xx", api.metricServerError(MetricOptions.builder().period(Duration.minutes(5)).statistic("Sum").build()), 5);
        alarm("SignalDispatchErrors", Metric.Builder.create().namespace("Agentic").metricName("SignalDispatchError")
                .period(Duration.minutes(15)).statistic("Sum").build(), 3);
        alarm("PremiumRequestsSpike", Metric.Builder.create().namespace("Agentic").metricName("PremiumRequests")
                .period(Duration.hours(1)).statistic("Sum").build(), 201);
        alarm("LambdaErrors", Function.metricAllErrors(MetricOptions.builder().period(Duration.minutes(5)).statistic("Sum").build()), 5);

        Rule.Builder.create(this, "KillSwitchChanged")
                .eventPattern(EventPattern.builder()
                        .source(List.of("aws.ssm"))
                        .detailType(List.of("Parameter Store Change"))
                        .detail(Map.of("name", Match.anyOf(Match.exactString("/agentic/enabled"), Match.prefix("/agentic/repos/"))))
                        .build())
                .targets(List.of(new SnsTopic(topic)))
                .build();
    }

    public Topic topic() {
        return topic;
    }

    private void alarm(String id, IMetric metric, double threshold) {
        Alarm.Builder.create(this, id)
                .alarmName("agentic-" + id)
                .metric(metric)
                .threshold(threshold)
                .evaluationPeriods(1)
                .comparisonOperator(ComparisonOperator.GREATER_THAN_OR_EQUAL_TO_THRESHOLD)
                .treatMissingData(TreatMissingData.NOT_BREACHING)
                .build()
                .addAlarmAction(new SnsAction(topic));
    }
}
