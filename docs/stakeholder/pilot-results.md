# Pilot results

Fill this page from the dashboard and the queries below at the end of the pilot. Every number must be traceable to a query or a document.

Pilot window: `____` to `____` · Repos: `____` · Team: `____`

## KPIs vs baseline

| KPI | Target | Baseline | Pilot | Status |
|---|---|---|---|---|
| Median lead time In Progress → PR ready (h) | −40% | from `docs/baseline.md` | | |
| Review rounds per merged PR | ≤ 1.5 | from `docs/baseline.md` | | |
| Merge rate of agent PRs | ≥ 60% | — | | |
| Escalation rate | < 25% | — | | |
| Reverts within 14 days | ≤ baseline | from `docs/baseline.md` | | |
| Premium requests per merged PR | stable/falling | — | | |
| Actions minutes per merged PR | stable/falling | — | | |

Status: ✅ met · ⚠️ close (within 10%) · ❌ missed.

```sql
SELECT sum(runs) AS runs, sum(merged) AS merged,
       approx_percentile(median_lead_time_h, 0.5) AS median_lead_time_h,
       avg(avg_review_rounds) AS review_rounds,
       CAST(sum(merged) AS double) / sum(runs) AS merge_rate,
       avg(escalation_rate) AS escalation_rate,
       avg(premium_per_merged_pr) AS premium_per_pr,
       avg(actions_min_per_merged_pr) AS actions_min_per_pr
FROM agentic.kpi_weekly
WHERE week BETWEEN date '____' AND date '____';
```

## Top escalation reasons

```sql
SELECT reason, sum(escalations) AS n FROM agentic.escalation_reasons
WHERE week BETWEEN date '____' AND date '____'
GROUP BY reason ORDER BY n DESC LIMIT 3;
```

| Reason | Count | What we changed |
|---|---|---|
| | | |

## Cost

| Item | Amount | Source |
|---|---|---|
| Copilot premium requests | | GitHub billing report for the service user |
| GitHub Actions minutes | | GitHub billing report |
| AWS | | Cost Explorer, tag `project=agentic` |

## Learning

- Lessons recorded: ____ · promoted to rules: ____ · expired: ____ (weekly digests)
- Avoided repeat mistake (from `docs/decisions.md`): ticket ____, PR ____ — one-paragraph story.

## Quotes

> "____" — pilot developer
