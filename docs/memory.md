# Memory

Lessons the agents learn from reviews, failed gates and escalations, plus engineering knowledge from Confluence and Jira, stored with embeddings for hybrid retrieval.

## Storage

- Aurora PostgreSQL Serverless v2 (16.x) with `pgvector`, reached through the **RDS Data API** (no VPC for Lambdas). Cluster `agentic-memory`; credentials in `agentic/memory-db`; ARNs in SSM `/agentic/memory/clusterArn` and `/agentic/memory/secretArn`.
- Locally: the `pgvector/pgvector:pg16` container on port `55432` with `MEMORY_MODE=jdbc` and `MEMORY_JDBC_URL`.
- Schema: `memory/src/main/resources/db/V1__lessons.sql`, applied by `MigrateHandler` (a CDK trigger after the cluster is created) and recorded in `schema_version`.

| Column | Meaning |
|---|---|
| `repo`, `paths[]`, `component`, `language`, `tags[]` | Where the lesson applies |
| `kind` | `review_feedback`, `gate_failure`, `stuck_postmortem`, `adr`, `spec` |
| `trigger`, `lesson` | The situation and the rule |
| `evidence[]` | Links to the PRs, comments or pages it came from |
| `embedding vector(1024)` | Bedrock Titan text embeddings v2 of `trigger + lesson` (normalised) |
| `search_tsv` | Generated full-text vector for keyword search |
| `hits`, `helped`, `ignored` | How often it recurred and whether it helped after injection |
| `status` | `active`, `promoted` (now a rule in `.github/instructions/`), `expired` |
| `scope` | `repo` (default) or `shared` (visible to every repo) |

## Writing

`LessonRepository.upsert`:

1. Scrubs secrets and PII from trigger, lesson and evidence (`Scrubber`).
2. Embeds `trigger + "\n" + lesson`.
3. Finds the nearest non-expired lesson in the same repo (or among shared lessons when the draft is shared).
4. Cosine similarity **> 0.9** → `hits + 1` and the evidence is appended; otherwise a new row is inserted.

## Inspecting

```bash
aws rds-data execute-statement --resource-arn "$(aws ssm get-parameter --name /agentic/memory/clusterArn --query Parameter.Value --output text)" \
  --secret-arn "$(aws ssm get-parameter --name /agentic/memory/secretArn --query Parameter.Value --output text)" \
  --database agentic --sql "select kind, status, count(*) from lessons group by kind, status"
```

Locally:

```bash
docker exec -it local-pgvector-1 psql -U agentic -d agentic -c "select trigger, lesson, hits from lessons order by hits desc limit 20"
```

## Deleting a lesson on request

```sql
DELETE FROM lessons WHERE id = '<uuid>';
```

Run it through `aws rds-data execute-statement` as above and note the request in `docs/decisions.md`.
