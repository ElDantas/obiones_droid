#!/usr/bin/env bash
set -euo pipefail
if [ $# -ne 1 ]; then
  echo "usage: $0 owner/repo" >&2
  exit 1
fi
repo="$(echo "$1" | tr '[:upper:]' '[:lower:]')"
current="$(aws ssm get-parameter --name /agentic/repos/allowlist --query Parameter.Value --output text)"
updated="$(echo "$current" | jq -c --arg r "$repo" '. + [$r] | unique')"
aws ssm put-parameter --name /agentic/repos/allowlist --value "$updated" --type String --overwrite >/dev/null
aws ssm put-parameter --name "/agentic/repos/${repo}/enabled" --value true --type String --overwrite >/dev/null
echo "Onboarded $repo"
