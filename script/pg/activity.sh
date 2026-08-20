#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/lib.sh"

section "Current activity"

query_table "
SELECT
  pid,
  usename,
  COALESCE(application_name, '-') AS application,
  client_addr,
  state,
  COALESCE(now() - xact_start, interval '0') AS xact_age,
  COALESCE(now() - query_start, interval '0') AS query_age,
  COALESCE(wait_event_type, '-') AS wait_type,
  COALESCE(wait_event, '-') AS wait_event,
  LEFT(regexp_replace(query, '[[:space:]]+', ' ', 'g'), 250) AS query
FROM pg_stat_activity
WHERE pid <> pg_backend_pid()
ORDER BY xact_start NULLS LAST, query_start NULLS LAST;
"
