package io.agentic.infra;

import software.amazon.awscdk.Stack;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.lambda.Function;

import java.util.List;

public final class Grants {
    private Grants() {
    }

    public static void common(Function fn) {
        Stack stack = Stack.of(fn);
        String region = stack.getRegion();
        String account = stack.getAccount();
        fn.addToRolePolicy(PolicyStatement.Builder.create()
                .actions(List.of("dynamodb:GetItem", "dynamodb:PutItem", "dynamodb:UpdateItem", "dynamodb:DeleteItem",
                        "dynamodb:Query", "dynamodb:ConditionCheckItem"))
                .resources(List.of(
                        "arn:aws:dynamodb:" + region + ":" + account + ":table/agentic-*",
                        "arn:aws:dynamodb:" + region + ":" + account + ":table/agentic-*/index/*"))
                .build());
        fn.addToRolePolicy(PolicyStatement.Builder.create()
                .actions(List.of("secretsmanager:GetSecretValue"))
                .resources(List.of("arn:aws:secretsmanager:" + region + ":" + account + ":secret:agentic/*"))
                .build());
        fn.addToRolePolicy(PolicyStatement.Builder.create()
                .actions(List.of("ssm:GetParameter"))
                .resources(List.of("arn:aws:ssm:" + region + ":" + account + ":parameter/agentic/*"))
                .build());
        fn.addToRolePolicy(PolicyStatement.Builder.create()
                .actions(List.of("states:StartExecution", "states:StopExecution", "states:SendTaskSuccess", "states:SendTaskFailure"))
                .resources(List.of(
                        "arn:aws:states:" + region + ":" + account + ":stateMachine:agentic-*",
                        "arn:aws:states:" + region + ":" + account + ":execution:agentic-*:*"))
                .build());
        fn.addToRolePolicy(PolicyStatement.Builder.create()
                .actions(List.of("states:SendTaskSuccess", "states:SendTaskFailure"))
                .resources(List.of("*"))
                .build());
    }

    public static void bedrock(Function fn) {
        fn.addToRolePolicy(PolicyStatement.Builder.create()
                .actions(List.of("bedrock:InvokeModel", "bedrock:Rerank"))
                .resources(List.of("*"))
                .build());
    }
}
