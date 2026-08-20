#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"

section "Table size / dead tuple indicators"
query_table "
SELECT
  schemaname,
  relname AS table_name,
  pg_size_pretty(pg_table_size(relid)) AS table_size,
  pg_size_pretty(pg_indexes_size(relid)) AS indexes_size,
  pg_size_pretty(pg_total_relation_size(relid)) AS total_size,
  n_live_tup,
  n_dead_tup,
  ROUND(
    100.0 * n_dead_tup /
    NULLIF(n_live_tup + n_dead_tup, 0), 2
  ) AS dead_ratio_pct
FROM pg_stat_user_tables
ORDER BY pg_total_relation_size(relid) DESC;
"
