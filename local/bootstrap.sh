#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
export AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test AWS_REGION=eu-west-2
AWS="aws --endpoint-url http://localhost:4566"
for i in $(seq 1 30); do
  curl -sf http://localhost:4566/_localstack/health >/dev/null && break
  sleep 2
done
for name in github-app github-service-user jira slack mcp; do
  $AWS secretsmanager put-secret-value --secret-id "agentic/$name" --secret-string "file://secrets/$name.json" >/dev/null
done
put() { $AWS ssm put-parameter --name "$1" --value "$2" --type String --overwrite >/dev/null; }
put /agentic/repos/allowlist '["acme/payments"]'
put /agentic/repos/acme/payments/enabled true
put /agentic/enabled true
docker exec -i local-pgvector-1 psql -q -U agentic -d agentic -v ON_ERROR_STOP=1 < ../memory/src/main/resources/db/V1__lessons.sql
docker exec -i local-pgvector-1 psql -q -U agentic -d agentic -c "CREATE TABLE IF NOT EXISTS schema_version (name text PRIMARY KEY, applied_at timestamptz NOT NULL DEFAULT now()); INSERT INTO schema_version (name) VALUES ('V1__lessons.sql') ON CONFLICT DO NOTHING;"
echo "LocalStack bootstrapped"
