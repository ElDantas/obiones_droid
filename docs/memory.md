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

## Retrieval

`HybridSearch.search(query)`:

1. Embeds the query text.
2. Takes the top 20 `active` lessons for the repo (plus `shared` ones) by vector distance, and the top 20 by full-text rank (`websearch_to_tsquery` + `ts_rank_cd`).
3. Fuses both lists with Reciprocal Rank Fusion (`1 / (60 + rank)`), then adds small boosts: +0.02 same repo, +0.01 overlapping path prefix (first two segments), +0.01 same component.
4. Reranks the top 20 with Bedrock rerank and keeps the requested number (8 for issue injection). If the reranker fails, the fused order is used.

`promoted` lessons are excluded (they already live in `.github/instructions/`), as are `expired` ones.

At `CONTEXT`, `MemoryLessonProvider` searches with the ticket summary and acceptance criteria and the first Jira component, injects up to 8 lessons into the issue under "Lessons from past work", and marks them used. Retrieval failures never block a run.

## MCP server

`POST <ApiUrl>/mcp` is a stateless MCP server (JSON-RPC 2.0 over streamable HTTP with plain JSON responses; no SSE, no sessions).

| Method | Behaviour |
|---|---|
| `initialize` | Echoes the client's protocol version if supported (`2025-11-25`, `2025-06-18`, `2025-03-26`), else the latest |
| `notifications/*` | HTTP 202, empty body |
| `tools/list` | `search_memory`, `record_lesson` |
| `tools/call search_memory` | `{query, paths?, limit?}` → markdown list of lessons |
| `tools/call record_lesson` | `{trigger, lesson, paths?, tags?}` → upsert with kind `review_feedback`, scope `repo`, evidence `mcp:<repo>` |

Authentication: `Authorization: Bearer <agentic/mcp bearerToken>`. The repo comes only from the `X-Agentic-Repo` header and must be allow-listed (403 otherwise); a `repo` field in tool input is ignored.

Try it with MCP Inspector: `npx @modelcontextprotocol/inspector`, transport "Streamable HTTP", URL `<ApiUrl>/mcp`, both headers set.

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
