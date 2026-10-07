package io.agentic.infra;

import software.amazon.awscdk.Duration;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.ssm.StringParameter;
import software.amazon.awscdk.services.stepfunctions.CatchProps;
import software.amazon.awscdk.services.stepfunctions.Choice;
import software.amazon.awscdk.services.stepfunctions.Condition;
import software.amazon.awscdk.services.stepfunctions.DefinitionBody;
import software.amazon.awscdk.services.stepfunctions.IntegrationPattern;
import software.amazon.awscdk.services.stepfunctions.JsonPath;
import software.amazon.awscdk.services.stepfunctions.Pass;
import software.amazon.awscdk.services.stepfunctions.Result;
import software.amazon.awscdk.services.stepfunctions.State;
import software.amazon.awscdk.services.stepfunctions.StateMachine;
import software.amazon.awscdk.services.stepfunctions.Succeed;
import software.amazon.awscdk.services.stepfunctions.TaskInput;
import software.amazon.awscdk.services.stepfunctions.Timeout;
import software.amazon.awscdk.services.stepfunctions.tasks.LambdaInvoke;
import software.constructs.Construct;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class OrchestratorStack extends Stack {
    private static final String PKG = "io.agentic.functions.tasks.";

    private final Map<String, String> env;
    private final Settings settings;
    private final List<JavaFunction> functions = new ArrayList<>();
    private final JavaFunction waitFn;
    private final StateMachine machine;

    public OrchestratorStack(Construct scope, String id, StackProps props, Map<String, String> env, Settings settings) {
        super(scope, id, props);
        this.env = env;
        this.settings = settings;
        this.waitFn = function("RegisterWaitFn", "RegisterWaitTask");

        LambdaInvoke readiness = call("Readiness", "ReadinessTask", "$.readiness");
        LambdaInvoke needsInfo = call("NeedsInfo", "NeedsInfoTask", JsonPath.DISCARD);
        LambdaInvoke context = call("Context", "ContextTask", JsonPath.DISCARD);
        LambdaInvoke assign = call("AssignCopilot", "AssignCopilotTask", JsonPath.DISCARD);
        LambdaInvoke waitPrReady = waitFor("WaitPrReady", "PR_READY", Timeout.at("$.readiness.codingTimeoutSeconds"));
        LambdaInvoke waitChecks = waitFor("WaitChecks", "CHECKS_COMPLETE", Timeout.duration(Duration.minutes(60)));
        LambdaInvoke evaluate = call("EvaluateGates", "EvaluateGatesTask", "$.result");
        LambdaInvoke fixing = call("Fixing", "FixingTask", "$.result");
        LambdaInvoke waitPrUpdated = waitFor("WaitPrUpdated", "PR_UPDATED", Timeout.at("$.readiness.codingTimeoutSeconds"));
        LambdaInvoke markHuman = call("MarkHumanReview", "MarkHumanReviewTask", JsonPath.DISCARD);
        LambdaInvoke waitHuman = waitFor("WaitHumanOutcome", "HUMAN_OUTCOME", Timeout.duration(Duration.hours(72)));
        LambdaInvoke remind = call("RemindReviewers", "RemindReviewersTask", JsonPath.DISCARD);
        LambdaInvoke humanFix = call("HumanFix", "HumanFixTask", "$.result");
        LambdaInvoke complete = call("Complete", "CompleteTask", JsonPath.DISCARD);
        LambdaInvoke abort = call("Abort", "AbortTask", JsonPath.DISCARD);
        LambdaInvoke escalate = call("Escalate", "EscalateTask", JsonPath.DISCARD);
        LambdaInvoke waitDecision = waitFor("WaitDecision", "ESCALATION_DECISION", Timeout.duration(Duration.hours(24)));
        LambdaInvoke applyDecision = call("ApplyDecision", "ApplyEscalationDecisionTask", "$.result");

        Succeed end = Succeed.Builder.create(this, "End").build();

        escalate.next(waitDecision);
        waitDecision.next(applyDecision);

        Pass escFromResult = Pass.Builder.create(this, "EscFromResult").inputPath("$.result.reason").resultPath("$.escalationReason").build();
        Pass escCodingTimeout = reason("EscCodingTimeout", "Timed out waiting for Copilot to finish coding");
        Pass escChecksTimeout = reason("EscChecksTimeout", "Timed out waiting for checks to complete");
        Pass escAmbiguous = reason("EscAmbiguousPr", "Copilot opened more than one PR for this ticket");
        Pass escInternal = reason("EscInternalError", "Internal orchestrator error; see #agentic-ops");
        for (Pass p : List.of(escFromResult, escCodingTimeout, escChecksTimeout, escAmbiguous, escInternal)) {
            p.next(escalate);
        }

        Pass abortClosed = abortReason("AbortPrClosed", "PR closed without merge");
        Pass abortSla = abortReason("AbortSla", "Escalation unanswered for 24h");
        Pass abortChosen = abortReason("AbortChosen", "Aborted from escalation");
        for (Pass p : List.of(abortClosed, abortSla, abortChosen)) {
            p.next(abort);
        }
        abort.next(end);
        complete.next(end);
        needsInfo.next(end);

        readiness.next(Choice.Builder.create(this, "ReadyChoice").build()
                .when(Condition.stringEquals("$.readiness.decision", "READY"), context)
                .otherwise(needsInfo));
        context.next(assign);
        assign.next(waitPrReady);
        waitPrReady.next(Choice.Builder.create(this, "AmbiguousChoice").build()
                .when(Condition.and(Condition.isPresent("$.signal.ambiguous"), Condition.booleanEquals("$.signal.ambiguous", true)), escAmbiguous)
                .otherwise(waitChecks));
        waitChecks.next(evaluate);
        evaluate.next(Choice.Builder.create(this, "GateChoice").build()
                .when(Condition.stringEquals("$.result.decision", "PASS"), markHuman)
                .when(Condition.stringEquals("$.result.decision", "FAIL"), fixing)
                .when(Condition.stringEquals("$.result.decision", "HUMAN_OVERRIDE"), markHuman)
                .otherwise(escFromResult));
        fixing.next(Choice.Builder.create(this, "FixChoice").build()
                .when(Condition.stringEquals("$.result.decision", "CONTINUE"), waitPrUpdated)
                .when(Condition.stringEquals("$.result.decision", "HUMAN_OVERRIDE"), markHuman)
                .otherwise(escFromResult));
        waitPrUpdated.next(waitChecks);
        markHuman.next(waitHuman);
        waitHuman.next(Choice.Builder.create(this, "OutcomeChoice").build()
                .when(Condition.stringEquals("$.signal.outcome", "MERGED"), complete)
                .when(Condition.stringEquals("$.signal.outcome", "CLOSED"), abortClosed)
                .otherwise(humanFix));
        remind.next(waitHuman);
        humanFix.next(Choice.Builder.create(this, "HumanFixChoice").build()
                .when(Condition.stringEquals("$.result.decision", "CONTINUE"), waitPrUpdated)
                .otherwise(escFromResult));
        applyDecision.next(Choice.Builder.create(this, "ResumeChoice").build()
                .when(Condition.stringEquals("$.result.decision", "RESUME_CODING"), waitPrReady)
                .when(Condition.stringEquals("$.result.decision", "RESUME_GATES"), evaluate)
                .when(Condition.stringEquals("$.result.decision", "RESUME_FIXING"), fixing)
                .when(Condition.stringEquals("$.result.decision", "TAKE_OVER"), markHuman)
                .otherwise(abortChosen));

        timeout(waitPrReady, escCodingTimeout);
        timeout(waitPrUpdated, escCodingTimeout);
        timeout(waitChecks, escChecksTimeout);
        timeout(waitHuman, remind);
        timeout(waitDecision, abortSla);
        for (LambdaInvoke t : List.of(readiness, context, assign, evaluate, fixing, markHuman, humanFix, applyDecision)) {
            t.addCatch(escInternal, CatchProps.builder().errors(List.of("States.ALL")).resultPath("$.error").build());
        }

        machine = StateMachine.Builder.create(this, "TicketRun")
                .stateMachineName("agentic-ticket-run")
                .definitionBody(DefinitionBody.fromChainable(readiness))
                .timeout(Duration.days(30))
                .tracingEnabled(!settings.local())
                .build();

        StringParameter.Builder.create(this, "ArnParam")
                .parameterName("/agentic/stateMachineArn")
                .stringValue(machine.getStateMachineArn())
                .build();

        for (JavaFunction f : functions) {
            Grants.common(f.function());
            Grants.bedrock(f.function());
        }
    }

    public StateMachine machine() {
        return machine;
    }

    public List<JavaFunction> functions() {
        return functions;
    }

    private JavaFunction function(String id, String handler) {
        JavaFunction fn = new JavaFunction(this, id, PKG + handler, env, settings);
        functions.add(fn);
        return fn;
    }

    private LambdaInvoke call(String id, String handler, String resultPath) {
        JavaFunction fn = function(id + "Fn", handler);
        return LambdaInvoke.Builder.create(this, id)
                .lambdaFunction(fn.target())
                .payloadResponseOnly(true)
                .resultPath(resultPath)
                .retryOnServiceExceptions(true)
                .build();
    }

    private LambdaInvoke waitFor(String id, String kind, Timeout timeout) {
        return LambdaInvoke.Builder.create(this, id)
                .lambdaFunction(waitFn.target())
                .integrationPattern(IntegrationPattern.WAIT_FOR_TASK_TOKEN)
                .payload(TaskInput.fromObject(Map.of(
                        "ticketKey", JsonPath.stringAt("$.ticketKey"),
                        "kind", kind,
                        "taskToken", JsonPath.getTaskToken())))
                .resultPath("$.signal")
                .taskTimeout(timeout)
                .build();
    }

    private Pass reason(String id, String text) {
        return Pass.Builder.create(this, id).result(Result.fromString(text)).resultPath("$.escalationReason").build();
    }

    private Pass abortReason(String id, String text) {
        return Pass.Builder.create(this, id).result(Result.fromString(text)).resultPath("$.abortReason").build();
    }

    private void timeout(LambdaInvoke wait, State target) {
        wait.addCatch(target, CatchProps.builder().errors(List.of("States.Timeout")).resultPath("$.error").build());
    }
}
