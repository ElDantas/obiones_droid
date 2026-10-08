#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
python3 - <<'PY' > /tmp/agentic-views.txt
import re
sql = open("analytics/athena/views.sql").read()
for stmt in [s.strip() for s in sql.split(";") if s.strip()]:
    print(stmt.replace("\n", " ") )
PY
while IFS= read -r stmt; do
  id="$(aws athena start-query-execution --work-group agentic --query-string "$stmt" --query QueryExecutionId --output text)"
  while true; do
    state="$(aws athena get-query-execution --query-execution-id "$id" --query QueryExecution.Status.State --output text)"
    case "$state" in
      SUCCEEDED) echo "ok: ${stmt:0:60}..."; break ;;
      FAILED|CANCELLED) aws athena get-query-execution --query-execution-id "$id" --query QueryExecution.Status.StateChangeReason --output text; exit 1 ;;
      *) sleep 2 ;;
    esac
  done
done < /tmp/agentic-views.txt
