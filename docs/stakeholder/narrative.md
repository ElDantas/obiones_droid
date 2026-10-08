# Agentic Dev Lifecycle — stakeholder narrative

Six parts, matching the Miro board. Each part has the message, the evidence to show and the talking points.

## 1. The problem

**Message:** Small, well-defined tickets still take days to reach review, and most of that time is waiting and context switching, not thinking.

**Show:** Baseline numbers from `docs/baseline.md`: median and p75 lead time from In Progress to PR ready, review rounds, revert rate.

**Say:**
- "A 3-point ticket spends most of its life queued behind other work."
- "We already pay for Copilot; today it only helps inside an editor."

## 2. The lifecycle

**Message:** One flag on a Jira ticket starts a controlled, observable pipeline that ends with a PR a human approves.

**Show:** Swimlane — Jira | Orchestrator | GitHub/Copilot | Humans:

1. Human flags the ticket `Agentic AI Approved` and moves it to In Progress.
2. Readiness gate checks acceptance criteria, repo, size and clarity; vague tickets bounce back with specific questions.
3. The orchestrator writes a precise GitHub issue (goal, AC, constraints, lessons) and assigns Copilot.
4. Copilot codes on a branch; CI, Copilot review and two gate agents (acceptance-criteria reviewer, QA) check every update.
5. Failures go back to Copilot as one consolidated instruction, within strict limits.
6. The PR is marked ready, reviewers are pinged, Jira moves to In Review.
7. A human reviews; change requests go back to Copilot; a human merges; Jira moves to Done.

**Say:** "Humans decide what goes in and what ships. The agent does the middle."

## 3. Trust and control

**Message:** The system is designed to stop and ask rather than guess.

**Show:**
- Human-only merge (no merge capability in the code; branch protection requires an approving review).
- Forbidden paths (infrastructure, migrations, CI, secrets) and diff-size limits.
- Budgets per ticket: 3 fix iterations, premium-request and Actions-minute caps, time limits.
- Loop detection: the same failure twice, no progress, or oscillating changes → immediate escalation.
- Escalation card in Slack with an AI diagnosis and Resume / Raise budget / Take over / Abort; only authorised people can act; 24h auto-abort.
- Kill switches (global and per repo) and a full audit ledger per ticket.

**Say:** "Every agent action is attributable, limited and reversible."

## 4. It gets smarter

**Message:** Review feedback becomes memory, and proven memory becomes rules.

**Show:** One real lesson's journey: reviewer comment → stored lesson → injected into the next similar ticket's issue → mistake avoided → promoted to `.github/instructions/` through a PR a human approved.

**Say:** "We teach it once. It stops repeating the mistake, and the rule becomes visible to every developer too."

## 5. Live demo

Follow `docs/demo-script.md` (10 minutes, with the fallback recording ready).

## 6. Results and the ask

**Show:** `docs/stakeholder/pilot-results.md` KPI table against the baseline; the QuickSight dashboard; two or three quotes from pilot developers.

**Ask:** See `docs/stakeholder/next-phase-proposal.md`.
