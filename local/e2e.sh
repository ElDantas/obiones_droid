#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
docker compose -f local/docker-compose.yml up -d
until curl -sf http://localhost:4566/_localstack/health >/dev/null; do sleep 2; done
until curl -sf http://localhost:8089/__admin/mappings >/dev/null; do sleep 1; done
if [ "${SKIP_DEPLOY:-false}" != "true" ]; then
  local/deploy.sh
fi
./mvnw -B -q -pl local-runner -am install -DskipTests
./mvnw -B -pl local-runner verify -Pe2e
