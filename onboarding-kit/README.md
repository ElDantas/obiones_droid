# Onboarding kit

Copy the contents of `.github/` into a pilot repository, adapt them, and open a PR.

| File | Purpose | Adapt? |
|---|---|---|
| `.github/copilot-instructions.md` | Repo-wide guidance Copilot reads on every task | Yes: build commands, architecture, conventions |
| `.github/workflows/copilot-setup-steps.yml` | Pre-installs the toolchain in Copilot's environment; the job must be named `copilot-setup-steps` | Yes: replace the Java/Maven steps to match the stack |
| `.github/agentic.yml` | Budgets, forbidden paths, reviewers, escalation approvers, required checks | Yes: reviewers, approvers, required check names |
| `.github/instructions/` | Path-scoped rules; promoted lessons land here | No (starts empty) |
| `.github/agents/ac-reviewer.agent.md` | Gate agent: checks the diff against acceptance criteria and conventions | Optional: tune the review bar |
| `.github/agents/qa-agent.agent.md` | Gate agent: lists missing tests as concrete test specs | Optional |
| `.github/agentic/verdict.schema.json` | Verdict format both agents write | No |
| `.github/workflows/agentic-gates.yml` | Runs both agents on `copilot/*` PRs and publishes `agentic/ac-review` and `agentic/qa` check runs | No; needs repo secret `AGENTIC_COPILOT_TOKEN` |

Set `gateAgents: true` in `.github/agentic.yml` only after the gate workflow is merged; the orchestrator then waits for both `agentic/*` checks before evaluating.

Repos can add forbidden paths but cannot remove the platform defaults.

After merging, a platform owner runs `tools/onboard-repo.sh owner/repo` to allow-list the repository.
