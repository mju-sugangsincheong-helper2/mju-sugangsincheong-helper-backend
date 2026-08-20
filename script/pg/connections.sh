#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"

section "Connections"
query_table "
SELECT
  usename,
  application_name,
  client_addr,
  state,
  count(*) AS connections
FROM pg_stat_activity
WHERE datname = current_database()
GROUP BY usename, application_name, client_addr, state
ORDER BY connections DESC;
"

query_table "
SELECT
  current_setting('max_connections')::int AS max_connections,
  count(*) AS current_connections,
  current_setting('max_connections')::int - count(*) AS remaining
FROM pg_stat_activity;
"
