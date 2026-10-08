CREATE OR REPLACE VIEW agentic.run_summary AS
SELECT
  ticketkey,
  runid,
  max(repo) AS repo,
  min(from_iso8601_timestamp(ts)) AS started_at,
  min(CASE WHEN "to" = 'HUMAN_REVIEW' THEN from_iso8601_timestamp(ts) END) AS pr_ready_at,
  max(CASE WHEN "to" = 'DONE' THEN from_iso8601_timestamp(ts) END) AS merged_at,
  max(CASE WHEN "to" = 'ABORTED' THEN 1 ELSE 0 END) AS aborted,
  max(CASE WHEN "to" = 'NEEDS_INFO' THEN 1 ELSE 0 END) AS needs_info,
  count_if("to" = 'ESCALATED') AS escalations,
  count_if("to" = 'HUMAN_FIX') AS human_rounds,
  max(premiumrequests) AS premium_requests,
  max(actionsminutes) AS actions_minutes
FROM agentic.ledger
GROUP BY ticketkey, runid;

CREATE OR REPLACE VIEW agentic.kpi_weekly AS
SELECT
  date_trunc('week', started_at) AS week,
  repo,
  count(*) AS runs,
  count(merged_at) AS merged,
  CAST(count(merged_at) AS double) / nullif(count(*) - sum(needs_info), 0) AS merge_rate,
  approx_percentile(date_diff('minute', started_at, pr_ready_at) / 60.0, 0.5) AS median_lead_time_h,
  avg(CASE WHEN merged_at IS NOT NULL THEN human_rounds + 1 END) AS avg_review_rounds,
  CAST(count_if(escalations > 0) AS double) / nullif(count(*), 0) AS escalation_rate,
  CAST(sum(premium_requests) AS double) / nullif(count(merged_at), 0) AS premium_per_merged_pr,
  CAST(sum(actions_minutes) AS double) / nullif(count(merged_at), 0) AS actions_min_per_merged_pr
FROM agentic.run_summary
GROUP BY 1, 2;

CREATE OR REPLACE VIEW agentic.escalation_reasons AS
SELECT
  date_trunc('week', from_iso8601_timestamp(ts)) AS week,
  repo,
  trim(split_part(reason, ':', 1)) AS reason,
  count(*) AS escalations
FROM agentic.ledger
WHERE "to" = 'ESCALATED'
GROUP BY 1, 2, 3;
