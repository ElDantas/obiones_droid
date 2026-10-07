package io.agentic.infra;

import org.junit.jupiter.api.Test;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.assertions.Template;

import java.util.Map;

class MemoryStackTest {

    @Test
    void serverlessAuroraWithDataApiAndParameters() throws Exception {
        StackProps props = StackProps.builder().env(Environment.builder().account("111111111111").region("eu-west-2").build()).build();
        Template t = Template.fromStack(new MemoryStack(new App(), "M", props, Map.of(), ApiStackTest.settings(false)));
        t.hasResourceProperties("AWS::RDS::DBCluster", Map.of(
                "Engine", "aurora-postgresql",
                "EnableHttpEndpoint", true,
                "StorageEncrypted", true,
                "DeletionProtection", true,
                "DBClusterIdentifier", "agentic-memory",
                "ServerlessV2ScalingConfiguration", Map.of("MinCapacity", 0.5, "MaxCapacity", 4)));
        t.hasResourceProperties("AWS::SSM::Parameter", Map.of("Name", "/agentic/memory/clusterArn"));
        t.resourceCountIs("AWS::EC2::NatGateway", 0);
    }
}
