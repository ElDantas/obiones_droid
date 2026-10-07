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

## Deploying to LocalStack

```bash
docker compose -f local/docker-compose.yml up -d
local/deploy.sh
aws --endpoint-url http://localhost:4566 --region eu-west-2 dynamodb list-tables
```

`local/deploy.sh` builds the functions jar, runs `cdklocal bootstrap` and `cdklocal deploy --all` with `-c agentic:local=true`, then `local/bootstrap.sh` loads the fake secrets from `local/secrets/` and the local SSM parameters (`acme/payments` is allow-listed).

With `agentic:local=true` the CDK app skips SnapStart, Lambda aliases, the HTTP API stack and the Aurora memory stack.

## Local API runner

API Gateway HTTP APIs are not available in the LocalStack community image, so `LocalApiServer` exposes the ingress handlers in-process:

```bash
./mvnw -q -pl local-runner -am install -DskipTests
./mvnw -q -pl local-runner exec:java
curl -s localhost:8080/health
```

| Route | Handler |
|---|---|
| `GET /health` | `HealthHandler` |
| `POST /jira/events` | `JiraEventHandler` (step 08) |
| `POST /github/webhook` | `GitHubWebhookHandler` (step 08) |
| `POST /slack/actions` | `SlackActionsHandler` (step 16) |
| `POST /mcp` | `McpHandler` (step 18) |

Routes whose handler does not exist yet return `501`. The runner points the AWS SDK at LocalStack (`aws.endpointUrl`), sets `AGENTIC_LLM_MODE=fake`, and points GitHub, Jira, Slack and Confluence at WireMock on `localhost:8089`.

## End-to-end scenarios

```bash
local/e2e.sh                     # start stack, deploy, run all *E2E tests
SKIP_DEPLOY=true local/e2e.sh    # reuse the current LocalStack deployment
```

The scenarios in `local-runner/src/test/java/io/agentic/local/e2e` drive the real state machine and Lambdas in LocalStack. WireMock (`local/wiremock/mappings`) stands in for Jira, GitHub and Slack, and signed webhooks stand in for Copilot.

| Scenario | Expected |
|---|---|
| Happy path | Execution `SUCCEEDED`, run `DONE`, one issue created, Copilot assigned once, PR marked ready, Jira `In Review` then `Done` |
| Early signal | Checks completing before the wait is registered still reach `HUMAN_REVIEW` |
| Vague ticket | `NEEDS_INFO`, Jira comment with "Clarify:", no issue created |
| Double fire | One execution, one issue |
| Failing checks | `FIXING`, one consolidated `@copilot` comment posted with the service-user token |
| Ambiguous PR | `ESCALATED` |
| Unflag | `ABORTED`, Copilot PR closed |

## Cloud-only checks

- Real Copilot coding agent behaviour and webhook timing
- Jira Automation rules firing
- Slack interactivity from the real Slack client
- Bedrock output quality (clarity scoring, lesson extraction, diagnosis, rerank)
- SnapStart, IAM permissions and QuickSight dashboards
- `StopExecution` on an execution waiting for a task token: LocalStack 4.14 returns 200 but leaves it `RUNNING`; the local E2E asserts the run is `ABORTED` and the PR closed instead

## Notes

- LocalStack is pinned to `4.14`, the last community image that runs without a LocalStack auth token. Newer images require `LOCALSTACK_AUTH_TOKEN`.
- pgvector is published on host port `55432` to avoid clashing with a local PostgreSQL on `5432`.
