package io.agentic.infra;

import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.aws_apigatewayv2_integrations.HttpLambdaIntegration;
import software.amazon.awscdk.services.apigatewayv2.AddRoutesOptions;
import software.amazon.awscdk.services.apigatewayv2.HttpApi;
import software.amazon.awscdk.services.apigatewayv2.HttpMethod;
import software.constructs.Construct;

import java.util.List;
import java.util.Map;

public class ApiStack extends Stack {
    private final HttpApi api;
    private final Map<String, String> env;
    private final Settings settings;

    public ApiStack(Construct scope, String id, StackProps props, Map<String, String> env, Settings settings) {
        super(scope, id, props);
        this.env = env;
        this.settings = settings;
        api = HttpApi.Builder.create(this, "Api").apiName("agentic-api").build();
        route("Health", "io.agentic.functions.HealthHandler", HttpMethod.GET, "/health");
        CfnOutput.Builder.create(this, "ApiUrl").value(api.getApiEndpoint()).build();
    }

    public JavaFunction route(String id, String handlerClass, HttpMethod method, String path) {
        JavaFunction fn = new JavaFunction(this, id + "Fn", handlerClass, env, settings);
        api.addRoutes(AddRoutesOptions.builder()
                .path(path)
                .methods(List.of(method))
                .integration(new HttpLambdaIntegration(id + "Integration", fn.target()))
                .build());
        return fn;
    }

    public HttpApi api() {
        return api;
    }
}
