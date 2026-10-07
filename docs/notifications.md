# Notifications

Tasks change run state only through `RunTransitions.moveTo`, which writes the ledger and then calls every notifier. A failing notifier is logged and never fails the task.

## Mapping

| To state | Jira status | Jira comment | Slack (thread in `#agentic-dev`) |
|---|---|---|---|
| any first transition | — | — | Root message: `🤖 ABC-123 · <summary> · <repo> · <Jira link>` |
| `NEEDS_INFO` | `Blocked (Agent)` | Missing items as bullets + Definition of Agent-Ready link | `ℹ️ Needs info: …` + DM to reporter |
| `CONTEXT` | — | — | `📝 Preparing the GitHub issue` |
| `CODING` | — | "Agent is implementing in <issue link>" | `⌨️ Copilot is coding: <issue link>` |
| `GATES` | — | — | `🔍 Running gates (iteration n)` |
| `FIXING` | — | — | `🔧 Fixing: <reason>` |
| `HUMAN_REVIEW` | `In Review` | "PR ready for review: <link>" | `👀 Ready for review: <PR link>` |
| `HUMAN_FIX` | — | — | `🔁 Addressing review comments` |
| `ESCALATED` | `Blocked (Agent)` | "Agent run paused: <reason>" | `⚠️ Paused: <reason>` + how to abort or restart |
| `DONE` | `Done` | "Merged: <link>" | `✅ Merged: <PR link>` |
| `ABORTED` | — | "Agent run aborted: <reason>" | `🛑 Aborted: <reason>` |

Leaving `ESCALATED` for an active state moves Jira back to `In Progress`.

All reason text goes through `Scrubber.scrub` before posting.

If the Jira workflow has no transition to the target status, the notifier logs a warning and posts once per ticket to `#agentic-ops`.

## Examples

```
🤖 ABC-123 · Cap discounts at 50% · acme/payments · https://corp.atlassian.net/browse/ABC-123
  ⌨️ Copilot is coding: https://github.com/acme/payments/issues/101
  🔍 Running gates (iteration 1)
  🔧 Fixing: 2 blocking finding(s)
  🔍 Running gates (iteration 2)
  👀 Ready for review: https://github.com/acme/payments/pull/418
  ✅ Merged: https://github.com/acme/payments/pull/418
```
