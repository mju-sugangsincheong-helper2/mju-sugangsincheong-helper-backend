#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"

section "Active PostgreSQL activity"
query_table "
SELECT
  pid,
  usename,
  application_name,
  client_addr,
  state,
  now() - query_start AS query_age,
  now() - xact_start AS xact_age,
  wait_event_type,
  wait_event,
  LEFT(query, 180) AS query
FROM pg_stat_activity
WHERE datname = current_database()
  AND pid <> pg_backend_pid()
  AND state <> 'idle'
ORDER BY COALESCE(xact_start, query_start) NULLS LAST;
"
