#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/lib.sh"

THRESHOLD="${1:-1 minute}"

section "Transactions older than $THRESHOLD"

query_table "
SELECT
  pid,
  usename,
  application_name,
  state,
  now() - xact_start AS transaction_age,
  now() - query_start AS query_age,
  backend_xmin,
  LEFT(regexp_replace(query, '[[:space:]]+', ' ', 'g'), 300) AS query
FROM pg_stat_activity
WHERE xact_start IS NOT NULL
  AND now() - xact_start > interval '$THRESHOLD'
ORDER BY xact_start;
"

section "Oldest transaction candidates for MVCC horizon"

query_table "
SELECT
  pid,
  usename,
  state,
  xact_start,
  now() - xact_start AS age,
  backend_xmin,
  LEFT(regexp_replace(query, '[[:space:]]+', ' ', 'g'), 220) AS query
FROM pg_stat_activity
WHERE xact_start IS NOT NULL
ORDER BY xact_start
LIMIT 20;
"
