package io.agentic.infra;

import org.junit.jupiter.api.Test;
import software.amazon.awscdk.App;
import software.amazon.awscdk.assertions.Match;
import software.amazon.awscdk.assertions.Template;

import java.util.Map;

class FoundationStackTest {

    private Template template() {
        App app = new App();
        return Template.fromStack(new FoundationStack(app, "F", null, Map.of()));
    }

    @Test
    void createsThreeTables() {
        Template t = template();
        t.resourceCountIs("AWS::DynamoDB::Table", 3);
        t.hasResourceProperties("AWS::DynamoDB::Table", Map.of("TableName", "agentic-runs"));
        t.hasResourceProperties("AWS::DynamoDB::Table", Map.of("TableName", "agentic-deliveries",
                "TimeToLiveSpecification", Map.of("AttributeName", "expiresAt", "Enabled", true)));
    }

    @Test
    void ledgerHasStreamAndWeekIndex() {
        template().hasResourceProperties("AWS::DynamoDB::Table", Map.of(
                "TableName", "agentic-ledger",
                "StreamSpecification", Map.of("StreamViewType", "NEW_IMAGE"),
                "GlobalSecondaryIndexes", Match.arrayWith(java.util.List.of(Match.objectLike(Map.of("IndexName", "byWeek"))))));
    }

    @Test
    void killSwitchDefaultsToTrue() {
        template().hasResourceProperties("AWS::SSM::Parameter", Map.of("Name", "/agentic/enabled", "Value", "true"));
    }

    @Test
    void createsAllSecrets() {
        Template t = template();
        t.resourceCountIs("AWS::SecretsManager::Secret", FoundationStack.SECRET_NAMES.size());
        t.hasResourceProperties("AWS::SecretsManager::Secret", Map.of("Name", "agentic/github-service-user"));
    }
}
