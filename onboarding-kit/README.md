# Onboarding kit

Copy the contents of `.github/` into a pilot repository, adapt them, and open a PR.

| File | Purpose | Adapt? |
|---|---|---|
| `.github/copilot-instructions.md` | Repo-wide guidance Copilot reads on every task | Yes: build commands, architecture, conventions |
| `.github/workflows/copilot-setup-steps.yml` | Pre-installs the toolchain in Copilot's environment; the job must be named `copilot-setup-steps` | Yes: replace the Java/Maven steps to match the stack |
| `.github/agentic.yml` | Budgets, forbidden paths, reviewers, escalation approvers, required checks | Yes: reviewers, approvers, required check names |
| `.github/instructions/` | Path-scoped rules; promoted lessons land here | No (starts empty) |

Repos can add forbidden paths but cannot remove the platform defaults.

After merging, a platform owner runs `tools/onboard-repo.sh owner/repo` to allow-list the repository.
