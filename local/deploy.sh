#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
export AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test AWS_REGION=eu-west-2 AWS_DEFAULT_REGION=eu-west-2
./mvnw -B -q -pl functions -am package -DskipTests
cd infra
npx --yes -p aws-cdk@2 -p aws-cdk-local@3 cdklocal bootstrap aws://000000000000/eu-west-2 -c agentic:local=true
npx --yes -p aws-cdk@2 -p aws-cdk-local@3 cdklocal deploy --all --require-approval never -c agentic:local=true
cd ..
local/bootstrap.sh
