#!/usr/bin/env bash
set -euo pipefail
if [ $# -lt 1 ]; then
  echo "usage: $0 TICKET-KEY [TICKET-KEY...]" >&2
  echo "env: JIRA_BASE_URL JIRA_EMAIL JIRA_API_TOKEN, plus gh and aws CLIs logged in" >&2
  exit 1
fi
: "${JIRA_BASE_URL:?set JIRA_BASE_URL}" "${JIRA_EMAIL:?set JIRA_EMAIL}" "${JIRA_API_TOKEN:?set JIRA_API_TOKEN}"
LABEL="Agentic AI Approved"
jira() { curl -sf -u "$JIRA_EMAIL:$JIRA_API_TOKEN" -H "Content-Type: application/json" "$@"; }

for key in "$@"; do
  echo "== $key"
  item="$(aws dynamodb get-item --table-name agentic-runs --key "{\"ticketKey\":{\"S\":\"$key\"}}" --output json || echo '{}')"
  repo="$(echo "$item" | jq -r '.Item.repo.S // empty')"
  issue="$(echo "$item" | jq -r '.Item.issueNumber.N // empty')"
  pr="$(echo "$item" | jq -r '.Item.prNumber.N // empty')"
  arn="$(echo "$item" | jq -r '.Item.executionArn.S // empty')"

  if [ -n "$arn" ]; then
    aws stepfunctions stop-execution --execution-arn "$arn" --cause "demo reset" >/dev/null 2>&1 || true
  fi
  if [ -n "$repo" ] && [ -n "$pr" ]; then
    branch="$(gh pr view "$pr" -R "$repo" --json headRefName -q .headRefName 2>/dev/null || true)"
    gh pr close "$pr" -R "$repo" --comment "Closed by demo reset" >/dev/null 2>&1 || true
    if [ -n "$branch" ] && [[ "$branch" == copilot/* ]]; then
      gh api -X DELETE "repos/$repo/git/refs/heads/$branch" >/dev/null 2>&1 || true
    fi
  fi
  if [ -n "$repo" ] && [ -n "$issue" ]; then
    gh issue close "$issue" -R "$repo" --comment "Closed by demo reset" >/dev/null 2>&1 || true
  fi
  if [ -n "$item" ] && [ "$item" != "{}" ]; then
    aws dynamodb update-item --table-name agentic-runs --key "{\"ticketKey\":{\"S\":\"$key\"}}" \
      --update-expression "SET #s = :a" --expression-attribute-names '{"#s":"state"}' \
      --expression-attribute-values '{":a":{"S":"ABORTED"}}' >/dev/null
  fi

  jira -X PUT "$JIRA_BASE_URL/rest/api/3/issue/$key" -d "{\"update\":{\"labels\":[{\"remove\":\"$LABEL\"}]}}" >/dev/null || true
  tid="$(jira "$JIRA_BASE_URL/rest/api/3/issue/$key/transitions" | jq -r '.transitions[] | select(.to.name=="To Do") | .id' | head -1)"
  if [ -n "$tid" ]; then
    jira -X POST "$JIRA_BASE_URL/rest/api/3/issue/$key/transitions" -d "{\"transition\":{\"id\":\"$tid\"}}" >/dev/null
  fi
  echo "   reset done"
done
