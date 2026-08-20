#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/lib.sh"

section "Top queries by mean execution time"

query_table "
SELECT
  calls,
  ROUND(total_exec_time::numeric, 2) AS total_ms,
  ROUND(mean_exec_time::numeric, 2) AS mean_ms,
  ROUND(max_exec_time::numeric, 2) AS max_ms,
  rows,
  shared_blks_hit,
  shared_blks_read,
  temp_blks_read,
  temp_blks_written,
  LEFT(regexp_replace(query, '[[:space:]]+', ' ', 'g'), 250) AS query
FROM pg_stat_statements
ORDER BY mean_exec_time DESC
LIMIT 30;
"

section "Top queries by total execution time"

query_table "
SELECT
  calls,
  ROUND(total_exec_time::numeric, 2) AS total_ms,
  ROUND(mean_exec_time::numeric, 2) AS mean_ms,
  rows,
  LEFT(regexp_replace(query, '[[:space:]]+', ' ', 'g'), 250) AS query
FROM pg_stat_statements
ORDER BY total_exec_time DESC
LIMIT 30;
"
