#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/lib.sh"

section "Dead tuples by table"

query_table "
SELECT
  schemaname,
  relname,
  n_live_tup,
  n_dead_tup,
  ROUND(
    100.0 * n_dead_tup /
    NULLIF(n_live_tup + n_dead_tup, 0), 2
  ) AS dead_ratio_pct,
  pg_size_pretty(pg_relation_size(relid)) AS table_size,
  pg_size_pretty(pg_indexes_size(relid)) AS index_size,
  pg_size_pretty(pg_total_relation_size(relid)) AS total_size,
  last_autovacuum,
  autovacuum_count
FROM pg_stat_user_tables
ORDER BY n_dead_tup DESC;
"

section "Highest dead-tuple tables"

query_table "
SELECT
  schemaname,
  relname,
  n_dead_tup,
  n_live_tup,
  ROUND(
    100.0 * n_dead_tup /
    NULLIF(n_live_tup + n_dead_tup, 0), 2
  ) AS dead_ratio_pct
FROM pg_stat_user_tables
WHERE n_dead_tup > 0
ORDER BY n_dead_tup DESC
LIMIT 30;
"
