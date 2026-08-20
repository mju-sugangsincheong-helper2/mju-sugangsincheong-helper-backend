#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/lib.sh"

section "Table / index size and dead tuples"

query_table "
SELECT
  schemaname,
  relname,
  pg_size_pretty(pg_relation_size(relid)) AS table_size,
  pg_size_pretty(pg_indexes_size(relid)) AS index_size,
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

section "Largest indexes"

query_table "
SELECT
  schemaname,
  relname AS table_name,
  indexrelname AS index_name,
  pg_size_pretty(pg_relation_size(indexrelid)) AS index_size,
  idx_scan,
  idx_tup_read,
  idx_tup_fetch
FROM pg_stat_user_indexes
ORDER BY pg_relation_size(indexrelid) DESC
LIMIT 50;
"
