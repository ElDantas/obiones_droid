# Next-phase proposal

Three options, which can be combined. Effort assumes the current platform team; costs are run-rate estimates to refine with pilot numbers.

| Option | What | Effort | Cost drivers | Main risk | Recommendation |
|---|---|---|---|---|---|
| **A. Scale out** | Onboard 5–10 more repos across 2–3 teams; self-service onboarding (kit + `tools/onboard-repo.sh` + docs) | 3–4 weeks | Copilot premium requests and Actions minutes grow linearly with tickets; AWS stays small | Ticket quality varies by team, raising escalations | **Do first.** Lowest risk, directly multiplies pilot value |
| **B. Multi-repo tickets** | One ticket coordinating changes across repos (linked issues, ordered PRs, joint gating) | 6–8 weeks | More Copilot sessions per ticket | Coordination failures across repos; harder rollback | Defer until A shows stable escalation rates |
| **C. Delivery stage** | After human merge, watch deploy pipelines and post-deploy health; auto-propose reverts on regressions (human approves) | 4–6 weeks | Pipeline integration work | Touches production paths; needs SRE buy-in | Pilot with one service after A |

## Recommendation

Approve **A** now with a go/no-go after 6 weeks based on: merge rate ≥ 60%, escalation rate < 25%, and lead time improvement holding at the larger scale. Start discovery for **C** with SRE in parallel. Revisit **B** next quarter.

## Ask

- Headcount: platform team continues at current size for Option A.
- Budget: Copilot premium-request allowance for the service user sized from pilot cost per merged PR × expected tickets.
- Sponsorship: one engineering manager per new team to own ticket quality (Definition of Agent-Ready).
