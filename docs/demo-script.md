# Phase 1 demo script (10 minutes)

**Cast:** presenter (drives Jira and narrates), helper (watches Slack and Step Functions, ready to switch to the recording).

**Before the session**

- Run `tools/demo/reset-demo.sh <IDEAL> <VAGUE> <BACKUP>` and confirm all three tickets are in `To Do` without the label.
- Open tabs: Jira board, the ideal ticket, `#agentic-dev`, Step Functions console (`agentic-ticket-run`), the pilot repo's Issues and Pull requests, the Definition of Agent-Ready page, the fallback recording.
- Check `/agentic/enabled` is `true` and the pilot repo is allow-listed.

| Time | Step | Say | Show |
|---|---|---|---|
| 0:00 | Context | "Today a developer picks a ticket, codes, opens a PR and waits for review. We let an agent do the middle part, with humans in control at both ends." | Jira ticket with clear acceptance criteria |
| 0:45 | The bar | "Only tickets that meet this bar can be handed to the agent." | Definition of Agent-Ready |
| 1:30 | Trigger | "I add the label and move it to In Progress — that's all." | Apply `Agentic AI Approved`, move to In Progress |
| 2:00 | Live state | "Every ticket gets one run. You can see exactly where it is." | Step Functions graph: Readiness → Context → AssignCopilot → WaitPrReady |
| 2:30 | Issue | "The agent turns the ticket into a precise brief for Copilot, including constraints and past lessons." | Generated GitHub issue |
| 3:00 | Bad ticket | "Meanwhile, a vague ticket is bounced back with specific questions instead of wasting compute." | Flag the vague ticket; show `Blocked (Agent)` and the "Clarify:" comment |
| 4:30 | Coding | "Copilot is working on a branch; it can't touch infrastructure, migrations or CI." | Slack thread; Copilot's draft PR |
| 6:00 | Gates | "CI and Copilot review run automatically. If something fails, the agent gets one consolidated instruction to fix it, up to a hard limit." | PR checks, Slack "Running gates" |
| 7:30 | Hand-off | "Only now does a human get involved: the PR is ready, Jira says In Review, reviewers are pinged." | PR ready for review, Jira status |
| 8:30 | Merge | "A human approves and merges. The agent never merges." | Approve and merge; Jira moves to Done; Slack ✅ |
| 9:30 | Wrap | "Everything you saw is logged per ticket, and it all runs on our existing Copilot licence." | Slack thread end to end |

## If it stalls

| Symptom | Say | Do |
|---|---|---|
| Copilot still coding at 6:00 | "Copilot typically takes 10–20 minutes on a real change; here's one we ran earlier." | Switch to the recording at the Gates step |
| Run escalated | "This is the safety net: the agent stopped and asked for help instead of looping." | Show the Slack escalation message, then the recording |
| Nothing happens after flagging | "Let me show you a recorded run while we check the trigger." | Helper checks the Jira Automation audit log and `#agentic-ops` |

## Fallback recording

Link: _add after the recording in step 13_
