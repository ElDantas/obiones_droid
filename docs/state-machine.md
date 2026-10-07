# Ticket run state machine

One Step Functions execution (`agentic-ticket-run`) per ticket run. Defined in `infra/.../OrchestratorStack.java`.

## Flow

```
Readiness ─READY─▶ Context ▶ AssignCopilot ▶ WaitPrReady ─ambiguous─▶ Esc
   │NOT_READY / PAUSED ▶ NeedsInfo ▶ End       │
                                               ▼
                                   ┌──────▶ WaitChecks ▶ EvaluateGates
                                   │          PASS ▶ MarkHumanReview ▶ WaitHumanOutcome
                                   │          FAIL ▶ Fixing ─CONTINUE▶ WaitPrUpdated ─┐
                                   │          ESCALATE ▶ Esc   HUMAN_OVERRIDE ▶ MarkHumanReview
                                   └──────────────────────────────────────────────────┘
WaitHumanOutcome: MERGED ▶ Complete ▶ End · CLOSED ▶ Abort ▶ End
                  CHANGES_REQUESTED ▶ HumanFix ─CONTINUE▶ WaitPrUpdated │ ESCALATE ▶ Esc
                  timeout 72h ▶ RemindReviewers ▶ WaitHumanOutcome
Esc ▶ Escalate ▶ WaitDecision (24h, timeout ▶ Abort) ▶ ApplyDecision
      RESUME_CODING ▶ WaitPrReady · RESUME_GATES ▶ EvaluateGates · RESUME_FIXING ▶ Fixing
      TAKE_OVER ▶ MarkHumanReview · ABORT ▶ Abort
```

## Task contract

Every task handler in `io.agentic.functions.tasks` implements `RequestHandler<Map<String,Object>, Map<String,Object>>` and receives the whole execution state.

| Handler | Output → path | `decision` values |
|---|---|---|
| `ReadinessTask` | `$.readiness` = `{decision, reasons[], codingTimeoutSeconds}` | `READY`, `NOT_READY`, `PAUSED` |
| `NeedsInfoTask` | discarded | — |
| `ContextTask` | discarded | — |
| `AssignCopilotTask` | discarded | — |
| `RegisterWaitTask` | callback payload → `$.signal` | — |
| `EvaluateGatesTask` | `$.result` = `{decision, reason}` | `PASS`, `FAIL`, `ESCALATE`, `HUMAN_OVERRIDE` |
| `FixingTask` | `$.result` | `CONTINUE`, `ESCALATE`, `HUMAN_OVERRIDE` |
| `MarkHumanReviewTask` | discarded | — |
| `RemindReviewersTask` | discarded | — |
| `HumanFixTask` | `$.result` | `CONTINUE`, `ESCALATE` |
| `CompleteTask` | discarded | — |
| `AbortTask` | discarded | — |
| `EscalateTask` | discarded | — |
| `ApplyEscalationDecisionTask` | `$.result` | `RESUME_CODING`, `RESUME_GATES`, `RESUME_FIXING`, `TAKE_OVER`, `ABORT` |

## Waits

Wait states call `RegisterWaitTask` with a task token. Ingress handlers deliver signals through `RunStore.deliverSignal`; a signal that arrives before the wait is registered is stored as pending and completes the wait immediately when it registers.

| Wait | Signal | Timeout | On timeout |
|---|---|---|---|
| `WaitPrReady` | `PR_READY` | `budgets.codingTimeout` (default 60 min) | Escalate: "Timed out waiting for Copilot to finish coding" |
| `WaitChecks` | `CHECKS_COMPLETE` | 60 min | Escalate: "Timed out waiting for checks to complete" |
| `WaitPrUpdated` | `PR_UPDATED` | `budgets.codingTimeout` | Escalate: "Timed out waiting for Copilot to finish coding" |
| `WaitHumanOutcome` | `HUMAN_OUTCOME` | 72 h | Remind reviewers, keep waiting |
| `WaitDecision` | `ESCALATION_DECISION` | 24 h | Abort: "Escalation unanswered for 24h" |

Any unhandled error in a work task escalates with "Internal orchestrator error; see #agentic-ops".
