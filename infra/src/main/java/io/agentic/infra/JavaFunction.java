package io.agentic.infra;

import software.amazon.awscdk.Duration;
import software.amazon.awscdk.services.lambda.Alias;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.IFunction;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.lambda.SnapStartConf;
import software.constructs.Construct;

import java.util.Map;

public class JavaFunction extends Construct {
    private final Function function;
    private final IFunction target;

    public JavaFunction(Construct scope, String id, String handlerClass, Map<String, String> env, Settings settings) {
        super(scope, id);
        Function.Builder b = Function.Builder.create(this, "Fn")
                .runtime(Runtime.JAVA_21)
                .handler(handlerClass + "::handleRequest")
                .code(Code.fromAsset(settings.functionsJar()))
                .memorySize(1024)
                .timeout(Duration.seconds(60))
                .environment(env);
        if (!settings.local()) {
            b.snapStart(SnapStartConf.ON_PUBLISHED_VERSIONS);
        }
        function = b.build();
        target = settings.local()
                ? function
                : Alias.Builder.create(this, "Live").aliasName("live").version(function.getCurrentVersion()).build();
    }

    public Function function() {
        return function;
    }

    public IFunction target() {
        return target;
    }
}
