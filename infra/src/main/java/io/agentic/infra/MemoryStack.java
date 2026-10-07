package io.agentic.infra;

import software.amazon.awscdk.Duration;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.ec2.SubnetConfiguration;
import software.amazon.awscdk.services.ec2.SubnetSelection;
import software.amazon.awscdk.services.ec2.SubnetType;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.rds.AuroraPostgresClusterEngineProps;
import software.amazon.awscdk.services.rds.AuroraPostgresEngineVersion;
import software.amazon.awscdk.services.rds.ClusterInstance;
import software.amazon.awscdk.services.rds.Credentials;
import software.amazon.awscdk.services.rds.CredentialsFromUsernameOptions;
import software.amazon.awscdk.services.rds.DatabaseCluster;
import software.amazon.awscdk.services.rds.DatabaseClusterEngine;
import software.amazon.awscdk.services.ssm.StringParameter;
import software.amazon.awscdk.triggers.Trigger;
import software.constructs.Construct;

import java.util.List;
import java.util.Map;

public class MemoryStack extends Stack {
    private final DatabaseCluster cluster;

    public MemoryStack(Construct scope, String id, StackProps props, Map<String, String> env, Settings settings) {
        super(scope, id, props);
        Vpc vpc = Vpc.Builder.create(this, "Vpc")
                .maxAzs(2)
                .natGateways(0)
                .subnetConfiguration(List.of(SubnetConfiguration.builder().name("isolated").subnetType(SubnetType.PRIVATE_ISOLATED).build()))
                .build();
        cluster = DatabaseCluster.Builder.create(this, "Cluster")
                .clusterIdentifier("agentic-memory")
                .engine(DatabaseClusterEngine.auroraPostgres(AuroraPostgresClusterEngineProps.builder()
                        .version(AuroraPostgresEngineVersion.of("16.6", "16")).build()))
                .writer(ClusterInstance.serverlessV2("writer"))
                .serverlessV2MinCapacity(0.5)
                .serverlessV2MaxCapacity(4)
                .enableDataApi(true)
                .credentials(Credentials.fromGeneratedSecret("agentic", CredentialsFromUsernameOptions.builder().secretName("agentic/memory-db").build()))
                .defaultDatabaseName("agentic")
                .storageEncrypted(true)
                .deletionProtection(true)
                .removalPolicy(RemovalPolicy.RETAIN)
                .vpc(vpc)
                .vpcSubnets(SubnetSelection.builder().subnetType(SubnetType.PRIVATE_ISOLATED).build())
                .build();

        StringParameter.Builder.create(this, "ClusterArnParam").parameterName("/agentic/memory/clusterArn").stringValue(cluster.getClusterArn()).build();
        StringParameter.Builder.create(this, "SecretArnParam").parameterName("/agentic/memory/secretArn")
                .stringValue(cluster.getSecret().getSecretArn()).build();

        JavaFunction migrate = new JavaFunction(this, "MigrateFn", "io.agentic.functions.memory.MigrateHandler", env, settings);
        Grants.common(migrate.function());
        migrate.function().addEnvironment("MEMORY_CLUSTER_ARN", cluster.getClusterArn());
        migrate.function().addEnvironment("MEMORY_SECRET_ARN", cluster.getSecret().getSecretArn());
        cluster.grantDataApiAccess(migrate.function());
        Trigger.Builder.create(this, "Migrate")
                .handler(migrate.function())
                .executeAfter(List.of(cluster))
                .timeout(Duration.minutes(5))
                .build();
    }

    public DatabaseCluster cluster() {
        return cluster;
    }
}
