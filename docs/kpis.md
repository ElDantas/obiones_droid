# KPIs

Every state transition is written to the `agentic-ledger` table, streamed (DynamoDB stream → `LedgerExporter` → Firehose) to `s3://agentic-analytics-<account>/ledger/dt=YYYY-MM-DD/` as gzipped JSON, and queried in Athena (workgroup `agentic`, database `agentic`, table `ledger` with partition projection). Apply the views with `tools/apply-views.sh`.

| Question | KPI | Source | Target | Baseline |
|---|---|---|---|---|
| Faster? | Median lead time In Progress → PR ready | `kpi_weekly.median_lead_time_h` | −40% vs baseline | `docs/baseline.md` median |
| Faster? | Human hands-on time per ticket | Pilot survey + `avg_review_rounds` | < 1h for S/M | — |
| Good? | Merge rate of agent PRs | `kpi_weekly.merge_rate` (NEEDS_INFO runs excluded) | ≥ 60% | — |
| Good? | Review rounds per merged PR | `kpi_weekly.avg_review_rounds` | ≤ 1.5 | `docs/baseline.md` median review rounds |
| Good? | Reverts within 14 days | Baseline tool re-run on agent PRs | ≤ baseline | `docs/baseline.md` revert rate |
| Learning? | Repeat-mistake rate | Lessons `ignored / (helped + ignored)` per month | Falling | — |
| Learning? | Lessons promoted | Weekly digest | Rising, then plateau | — |
| Under control? | Escalation rate and reasons | `kpi_weekly.escalation_rate`, `escalation_reasons` | < 25% | — |
| Worth it? | Premium requests and Actions minutes per merged PR | `kpi_weekly.premium_per_merged_pr`, `actions_min_per_merged_pr` | Stable or falling | — |

## Ops alarms (SNS `agentic-ops` → `#agentic-ops`)

| Alarm | Condition |
|---|---|
| `agentic-ExecutionsFailed` | ≥ 1 failed execution in 5 min |
| `agentic-ExecutionsTimedOut` | ≥ 1 timed-out execution in 1 h |
| `agentic-Api5xx` | ≥ 5 API 5xx in 5 min |
| `agentic-SignalDispatchErrors` | ≥ 3 webhook dispatch errors in 15 min |
| `agentic-PremiumRequestsSpike` | > 200 premium requests in 1 h across all runs |
| `agentic-LambdaErrors` | ≥ 5 Lambda errors in 5 min (all functions) |
| Kill switch changed | EventBridge: SSM change to `/agentic/enabled` or `/agentic/repos/*` |

## Dashboard

QuickSight dataset on `agentic.kpi_weekly` and `agentic.run_summary`; one sheet per question above, with the baseline as a reference line. Link: _add after the dashboard is built_.
