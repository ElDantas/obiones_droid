# Guardrails

## Budgets

Defaults live in SSM `/agentic/budgets/defaults`; a repo can override them in `.github/agentic.yml` under `budgets:` and can only add forbidden paths.

| Guardrail | Default | Checked in | On breach |
|---|---|---|---|
| Gate fix iterations | 3 | `FixingTask` | Escalate |
| Human-fix iterations | 3 | `HumanFixTask` | Escalate ("the ticket may be underspecified") |
| Premium requests | soft 30 / hard 50 | `EvaluateGatesTask`, `FixingTask`, `HumanFixTask` | soft: one Slack warning per run; hard: escalate |
| Actions minutes | soft 120 / hard 240 | same | same |
| `CODING` wall-clock | 60 min | Step Functions wait timeout | Escalate |
| Total run age | 3 days | same tasks | Escalate |
| Diff size | ≤ 800 lines, ≤ 25 files | `EvaluateGatesTask` (`ScopeGuard`) | Escalate, no retry |
| Forbidden paths | `infra/**`, `**/migrations/**`, `**/*.pem`, `**/*.key`, `**/.env*`, `.github/workflows/**` | same | Escalate, no retry |

### How usage is measured

- **Premium requests** (estimate): +1 per Copilot coding session (assignment and every `@copilot` instruction) plus each gate agent's reported `premiumRequests` (counted once per check run). The monthly GitHub billing report is the source of truth; compare it with the dashboard monthly.
- **Actions minutes**: every `workflow_run.completed` event for the agent PR adds `ceil((updated_at − run_started_at) / 60s)`, counted once per workflow run.
- Usage updates use optimistic locking on the run item, so webhook-driven and task-driven updates never overwrite each other.

## Loop detection

After every gate evaluation a snapshot is stored: failing finding IDs, their fingerprint, failing files, files touched since the previous evaluation, and content hashes of those files. Before each fixing iteration, `LoopDetector` checks the history and escalates immediately when:

| Rule | Example |
|---|---|
| **Same failure repeated**: the last two fingerprints are identical | `ci:build:OrderServiceTest:discount_rounding` fails, Copilot changes something, the same test fails again |
| **No progress**: the latest commits touched none of the previously failing files | Failing test is in `Order.java`; Copilot only edited `README.md` |
| **Oscillation**: a file's content returns to an earlier version | `Order.java` goes x → y → x across three iterations |

After a human resumes or raises the budget from an escalation, loop detection is skipped for exactly one iteration.

## Changing budgets for a repo

```yaml
budgets:
  maxGateIterations: 5
  premiumHard: 80
  codingTimeoutMinutes: 90
forbiddenPaths:
  - "legacy/**"
```
