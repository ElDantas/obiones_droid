# Local testing

## Prerequisites

- Docker
- Java 21 (the Maven Wrapper downloads Maven)
- Node.js (for `aws-cdk-local`, used from step 05a)

## Test levels

| Kind | Naming | Command | Needs Docker |
|---|---|---|---|
| Unit | `*Test.java` | `./mvnw test` | No |
| Integration | `*IT.java` | `./mvnw verify` | Yes |
| Local end-to-end | `*E2E.java` | `local/e2e.sh` | Yes |

## Local stack

```bash
docker compose -f local/docker-compose.yml up -d
curl -s localhost:4566/_localstack/health
curl -s localhost:8089/__admin/mappings
```

| Real system | Local stand-in |
|---|---|
| AWS (Lambda, Step Functions, DynamoDB, SSM, Secrets Manager, SNS, EventBridge, S3) | LocalStack on `localhost:4566` |
| GitHub, Jira, Slack, Confluence | WireMock on `localhost:8089` (stubs in `local/wiremock`) |
| Bedrock (text, embeddings, rerank) | Deterministic fakes, `AGENTIC_LLM_MODE=fake` |
| Aurora PostgreSQL + pgvector | `pgvector/pgvector:pg16` on `localhost:55432`, `MEMORY_MODE=jdbc` |
| API Gateway | `LocalApiServer` on `localhost:8080` (step 05a) |

## Cloud-only checks

- Real Copilot coding agent behaviour and webhook timing
- Jira Automation rules firing
- Slack interactivity from the real Slack client
- Bedrock output quality (clarity scoring, lesson extraction, diagnosis, rerank)
- SnapStart, IAM permissions and QuickSight dashboards

## Notes

- LocalStack is pinned to `4.14`, the last community image that runs without a LocalStack auth token. Newer images require `LOCALSTACK_AUTH_TOKEN`.
- pgvector is published on host port `55432` to avoid clashing with a local PostgreSQL on `5432`.
