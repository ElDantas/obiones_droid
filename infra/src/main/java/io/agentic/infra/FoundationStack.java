package io.agentic.infra;

import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.dynamodb.Attribute;
import software.amazon.awscdk.services.dynamodb.AttributeType;
import software.amazon.awscdk.services.dynamodb.BillingMode;
import software.amazon.awscdk.services.dynamodb.GlobalSecondaryIndexProps;
import software.amazon.awscdk.services.dynamodb.PointInTimeRecoverySpecification;
import software.amazon.awscdk.services.dynamodb.StreamViewType;
import software.amazon.awscdk.services.dynamodb.Table;
import software.amazon.awscdk.services.secretsmanager.Secret;
import software.amazon.awscdk.services.ssm.StringParameter;
import software.constructs.Construct;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FoundationStack extends Stack {
    public static final String DEFAULT_BUDGETS_JSON = "{\"maxGateIterations\":3,\"maxHumanIterations\":3,\"premiumSoft\":30,\"premiumHard\":50,"
            + "\"codingTimeout\":\"PT1H\",\"maxRunAge\":\"P3D\",\"actionsMinutesSoft\":120,\"actionsMinutesHard\":240,"
            + "\"maxDiffLines\":800,\"maxDiffFiles\":25,\"forbiddenPaths\":[\"infra/**\",\"**/migrations/**\",\"**/*.pem\",\"**/*.key\",\"**/.env*\",\".github/workflows/**\"]}";

    public static final List<String> SECRET_NAMES = List.of(
            "agentic/github-app", "agentic/github-service-user", "agentic/jira", "agentic/slack", "agentic/mcp");

    private final Table runs;
    private final Table ledger;
    private final Table deliveries;
    private final List<Secret> secrets;

    public FoundationStack(Construct scope, String id, StackProps props, Map<String, String> parameterOverrides) {
        this(scope, id, props, parameterOverrides, false);
    }

    public FoundationStack(Construct scope, String id, StackProps props, Map<String, String> parameterOverrides, boolean local) {
        super(scope, id, props);

        runs = Table.Builder.create(this, "Runs")
                .tableName("agentic-runs")
                .partitionKey(Attribute.builder().name("ticketKey").type(AttributeType.STRING).build())
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .pointInTimeRecoverySpecification(PointInTimeRecoverySpecification.builder().pointInTimeRecoveryEnabled(true).build())
                .removalPolicy(RemovalPolicy.RETAIN)
                .build();
        runs.addGlobalSecondaryIndex(GlobalSecondaryIndexProps.builder()
                .indexName("byIssue").partitionKey(Attribute.builder().name("repoIssue").type(AttributeType.STRING).build()).build());
        runs.addGlobalSecondaryIndex(GlobalSecondaryIndexProps.builder()
                .indexName("byPr").partitionKey(Attribute.builder().name("repoPr").type(AttributeType.STRING).build()).build());

        Table.Builder ledgerBuilder = Table.Builder.create(this, "Ledger")
                .tableName("agentic-ledger")
                .partitionKey(Attribute.builder().name("ticketKey").type(AttributeType.STRING).build())
                .sortKey(Attribute.builder().name("tsSeq").type(AttributeType.STRING).build())
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .pointInTimeRecoverySpecification(PointInTimeRecoverySpecification.builder().pointInTimeRecoveryEnabled(true).build())
                .removalPolicy(RemovalPolicy.RETAIN);
        if (!local) {
            ledgerBuilder.stream(StreamViewType.NEW_IMAGE);
        }
        ledger = ledgerBuilder.build();
        ledger.addGlobalSecondaryIndex(GlobalSecondaryIndexProps.builder()
                .indexName("byWeek")
                .partitionKey(Attribute.builder().name("week").type(AttributeType.STRING).build())
                .sortKey(Attribute.builder().name("ts").type(AttributeType.STRING).build())
                .build());

        deliveries = Table.Builder.create(this, "Deliveries")
                .tableName("agentic-deliveries")
                .partitionKey(Attribute.builder().name("deliveryId").type(AttributeType.STRING).build())
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .timeToLiveAttribute("expiresAt")
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();

        secrets = SECRET_NAMES.stream()
                .map(name -> Secret.Builder.create(this, "Secret" + name.substring(name.indexOf('/') + 1))
                        .secretName(name)
                        .description("Set by a human; see docs/runbook.md")
                        .build())
                .toList();

        Map<String, String> params = new LinkedHashMap<>(defaultParameters());
        params.putAll(parameterOverrides);
        params.forEach((name, value) -> StringParameter.Builder.create(this, "Param" + name.replaceAll("[^A-Za-z0-9]", ""))
                .parameterName(name)
                .stringValue(value)
                .build());
    }

    public static Map<String, String> defaultParameters() {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("/agentic/enabled", "true");
        p.put("/agentic/repos/allowlist", "[]");
        p.put("/agentic/budgets/defaults", DEFAULT_BUDGETS_JSON);
        p.put("/agentic/jira/fields/targetRepo", "customfield_10123");
        p.put("/agentic/jira/fields/acceptanceCriteria", "none");
        p.put("/agentic/jira/fields/storyPoints", "customfield_10016");
        p.put("/agentic/readiness/maxStoryPoints", "5");
        p.put("/agentic/readiness/clarityThreshold", "70");
        p.put("/agentic/bedrock/textModelId", "anthropic.claude-sonnet-4-5-20250929-v1:0");
        p.put("/agentic/bedrock/embedModelId", "amazon.titan-embed-text-v2:0");
        p.put("/agentic/bedrock/rerankModelId", "amazon.rerank-v1:0");
        p.put("/agentic/slack/channels/dev", "#agentic-dev");
        p.put("/agentic/slack/channels/ops", "#agentic-ops");
        p.put("/agentic/github/serviceUser", "agentic-svc");
        p.put("/agentic/docs/agentReadyUrl", "unset");
        p.put("/agentic/confluence/syncSpaces", "[]");
        p.put("/agentic/confluence/syncLabels", "[\"adr\",\"spec\"]");
        p.put("/agentic/confluence/digestSpace", "unset");
        p.put("/agentic/confluence/digestParentId", "unset");
        p.put("/agentic/jira/syncProjects", "[]");
        return p;
    }

    public Table runs() {
        return runs;
    }

    public Table ledger() {
        return ledger;
    }

    public Table deliveries() {
        return deliveries;
    }

    public List<Secret> secrets() {
        return secrets;
    }
}
