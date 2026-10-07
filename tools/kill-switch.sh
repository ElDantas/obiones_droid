#!/usr/bin/env bash
set -euo pipefail
if [ $# -lt 1 ] || { [ "$1" != "on" ] && [ "$1" != "off" ]; }; then
  echo "usage: $0 on|off [owner/repo]" >&2
  exit 1
fi
value=$([ "$1" = "on" ] && echo true || echo false)
if [ $# -ge 2 ]; then
  repo="$(echo "$2" | tr '[:upper:]' '[:lower:]')"
  name="/agentic/repos/${repo}/enabled"
  scope="$repo"
else
  name="/agentic/enabled"
  scope="all repos"
fi
aws ssm put-parameter --name "$name" --value "$value" --type String --overwrite >/dev/null
echo "Agentic runs $1 for $scope ($name=$value)"
if [ -n "${SLACK_BOT_TOKEN:-}" ]; then
  channel="$(aws ssm get-parameter --name /agentic/slack/channels/ops --query Parameter.Value --output text 2>/dev/null || echo '#agentic-ops')"
  curl -sf -X POST https://slack.com/api/chat.postMessage \
    -H "Authorization: Bearer $SLACK_BOT_TOKEN" -H "Content-Type: application/json; charset=utf-8" \
    -d "{\"channel\":\"$channel\",\"text\":\"🔌 Kill switch: agentic runs turned *$1* for $scope by $(whoami)\"}" >/dev/null || true
fi
