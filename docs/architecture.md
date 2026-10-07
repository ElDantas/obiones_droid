# Architecture

## Components

| # | Component | Runs on | Responsibility |
|---|---|---|---|
| 1 | Jira Automation rule | Jira Cloud | On label `Agentic AI Approved` and status → In Progress, POST `{ticketKey}` to the Ingress API |
| 2 | Ingress API | API Gateway + Lambda | Verify Jira, GitHub and Slack signatures; start or resume the ticket's run |
| 3 | Ticket Orchestrator | Step Functions | One execution per ticket; owns states, counters, budgets, pause/resume |
| 4 | GitHub App `agentic-bot` | GitHub | Least-privilege identity: issues, comments, PR/review/check events. Never merges |
| 5 | Copilot coding agent | GitHub Actions | Implements changes, responds to `@copilot` feedback |
| 6 | Gate agents (AC reviewer, QA agent) | Actions jobs | Structured verdicts published as check runs |
| 7 | Memory service | Lambda + Aurora pgvector + Bedrock embeddings, MCP server | `search_memory`, `record_lesson`, promotion |
| 8 | Notifier | Lambda → Slack + Jira | Status updates, escalation cards |
| 9 | Run ledger and metrics | DynamoDB → S3/Athena → QuickSight; CloudWatch | Audit trail, KPIs, ops alarms |
| 10 | Repo onboarding kit | Template files | Copilot setup, instructions, gate workflows, budgets |

## Modules

| Module | Responsibility |
|---|---|
| `core` | Pure domain: states, transitions, budgets, scope guard, loop detection, scrubbing |
| `integrations` | GitHub, Jira, Slack, Confluence and Bedrock clients |
| `memory` | Lesson store, embeddings, hybrid retrieval, MCP protocol |
| `functions` | Lambda handlers |
| `infra` | CDK app and stacks |
| `tools/baseline` | Baseline metrics CLI |

## Ticket lifecycle

```
Jira (label + In Progress) ──webhook──▶ Ingress ─▶ Step Functions [ticket run]
  READINESS ──fail──▶ NEEDS_INFO
     │
  CONTEXT ─▶ CODING ─▶ GATES ──pass──▶ HUMAN_REVIEW ──merged──▶ DONE
                         │  ▲              │
                       fail│  │            changes requested
                         ▼  │              ▼
                       FIXING          HUMAN_FIX ─▶ GATES
  Any limit hit ─▶ ESCALATED ─▶ Resume | Raise budget | Take over | Abort ─▶ ABORTED
```
