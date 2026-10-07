# Gate agents

Two Copilot custom agents run as GitHub Actions jobs on every update to a `copilot/*` PR and publish verdicts as check runs the orchestrator reads.

| Check run | Agent profile | Checks | Blocks when |
|---|---|---|---|
| `agentic/ac-review` | `.github/agents/ac-reviewer.agent.md` | Each acceptance criterion is implemented **and** tested; repo conventions; "Lessons from past work" in the issue | Any criterion unmet or any violation |
| `agentic/qa` | `.github/agents/qa-agent.agent.md` | Acceptance criteria and changed behaviour without automated tests | Any gap; each finding is a concrete test specification Copilot implements in the next fixing iteration |

## Verdict

Each agent writes `verdict.json` (schema: `.github/agentic/verdict.schema.json`). The publish job embeds it in the check run's `output.text` as a fenced block tagged `agentic-verdict`:

```json
{
  "gate": "ac-review",
  "pass": false,
  "summary": "2 of 4 acceptance criteria not met",
  "findings": [
    {"id": "AC-3", "file": "src/main/java/Order.java", "line": 40, "message": "Discount is not capped at 50% as AC-3 requires"}
  ],
  "premiumRequests": 1
}
```

`GateCollector` turns every finding of a failing verdict into a blocking finding (`<gate>:<id>`). A check run without a parseable verdict blocks with "verdict missing or invalid".

## Workflow design

- **Job `review`** (matrix over both agents) has read-only permissions. It runs the Copilot CLI non-interactively:
  - `copilot -p "<prompt>" --agent <name> --allow-all-tools` (non-interactive mode requires `--allow-all-tools`)
  - plus `--deny-tool` for `git push`, `git commit`, `gh api`, `gh pr merge|review|comment`, `gh issue comment`, so the agent can read but not change anything on GitHub.
  - `COPILOT_GITHUB_TOKEN` = repo secret `AGENTIC_COPILOT_TOKEN` (service user PAT with "Copilot Requests"); `GH_TOKEN` = the job's read-only `github.token`.
  - If the agent fails or writes an invalid verdict, a failing placeholder verdict is written.
- **Job `publish`** (`checks: write`) downloads the verdict and creates the check run. Only this job can write to GitHub.

Verified against Copilot CLI 1.0.93 (`copilot --help`).

## Orchestrator behaviour

- `.github/agentic.yml` `gateAgents: true` makes the webhook router wait until both `agentic/ac-review` and `agentic/qa` exist and are completed for the PR head SHA before signalling `CHECKS_COMPLETE`. Leave it `false` until the workflow is merged in the repo.
- From the second gate iteration, Copilot code review comments are advisory; the AC reviewer is the judge of what still matters.

## Tuning

- Too strict: soften the "met only if" rule in `ac-reviewer.agent.md` or tell it to ignore style-only conventions.
- Too lenient: add explicit examples of failures to the profile.
- Re-run the workflow on already-merged agent PRs to compare verdicts before and after a change.

## Cost

Each update to a Copilot PR runs both agents: about 2 premium requests (reported as `premiumRequests` and added to the run's usage in step 15) plus a few Actions minutes.
