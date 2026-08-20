#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/lib.sh"

section "Blocked sessions"

query_table "
SELECT
  blocked.pid AS blocked_pid,
  blocked.usename AS blocked_user,
  now() - blocked.query_start AS blocked_for,
  blocking.pid AS blocking_pid,
  blocking.usename AS blocking_user,
  now() - blocking.query_start AS blocking_for,
  COALESCE(blocked.wait_event_type, '-') AS wait_type,
  COALESCE(blocked.wait_event, '-') AS wait_event,
  LEFT(regexp_replace(blocked.query, '[[:space:]]+', ' ', 'g'), 180) AS blocked_query,
  LEFT(regexp_replace(blocking.query, '[[:space:]]+', ' ', 'g'), 180) AS blocking_query
FROM pg_stat_activity AS blocked
CROSS JOIN LATERAL unnest(pg_blocking_pids(blocked.pid)) AS bp(blocking_pid)
JOIN pg_stat_activity AS blocking
  ON blocking.pid = bp.blocking_pid
ORDER BY blocked.query_start;
"

section "Lock summary"

query_table "
SELECT
  locktype,
  mode,
  granted,
  count(*) AS count
FROM pg_locks
GROUP BY locktype, mode, granted
ORDER BY granted, count DESC;
"
