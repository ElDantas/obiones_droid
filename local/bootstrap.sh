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
echo "LocalStack bootstrapped"
