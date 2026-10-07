# Runbook

## Build and deploy

```bash
./mvnw -B -pl functions -am package -DskipTests
cd infra
npx cdk bootstrap aws://ACCOUNT/REGION
npx cdk deploy --all
```

Set `agentic:account` and `agentic:region` in `infra/cdk.json` (or pass `-c agentic:account=… -c agentic:region=…`) before the first cloud deploy.

## Secrets

All secrets are created empty by `AgenticFoundation`. Set them once after the first deploy. Never commit the JSON files.

| Secret | JSON fields |
|---|---|
| `agentic/github-app` | `appId`, `installationId`, `privateKeyPem`, `webhookSecret` |
| `agentic/github-service-user` | `login`, `token` |
| `agentic/jira` | `baseUrl`, `email`, `apiToken`, `webhookToken` |
| `agentic/slack` | `botToken`, `signingSecret` |
| `agentic/mcp` | `bearerToken` |

```bash
aws secretsmanager put-secret-value --secret-id agentic/github-app --secret-string file://github-app.json
aws secretsmanager describe-secret --secret-id agentic/github-app
```

Generate random tokens with `openssl rand -hex 32`.

## Kill switches

| Scope | Parameter | Off |
|---|---|---|
| Global | `/agentic/enabled` | `aws ssm put-parameter --name /agentic/enabled --value false --type String --overwrite` |
| One repo | `/agentic/repos/<owner>/<repo>/enabled` | same command with the repo parameter |

Values are cached for 30 seconds by the Lambdas.

## Allow-list a repo

```bash
tools/onboard-repo.sh owner/repo
```

This appends the lowercase repo to `/agentic/repos/allowlist` and sets its `enabled` parameter to `true`.

## Inspect a run

```bash
aws dynamodb get-item --table-name agentic-runs --key '{"ticketKey":{"S":"ABC-123"}}'
aws dynamodb query --table-name agentic-ledger --key-condition-expression "ticketKey = :k" \
  --expression-attribute-values '{":k":{"S":"ABC-123"}}'
```
