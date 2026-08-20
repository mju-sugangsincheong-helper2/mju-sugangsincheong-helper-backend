#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/lib.sh"

section "Connection state"

query_table "
SELECT
  COALESCE(state, 'unknown') AS state,
  count(*) AS connections
FROM pg_stat_activity
GROUP BY state
ORDER BY connections DESC;
"

section "Connections by database"

query_table "
SELECT
  datname,
  count(*) AS connections
FROM pg_stat_activity
GROUP BY datname
ORDER BY connections DESC;
"

section "Connections by application"

query_table "
SELECT
  COALESCE(application_name, '-') AS application,
  count(*) AS connections
FROM pg_stat_activity
GROUP BY application_name
ORDER BY connections DESC;
"

section "Connection settings"

query "
SELECT name, setting
FROM pg_settings
WHERE name IN ('max_connections', 'superuser_reserved_connections');
"
