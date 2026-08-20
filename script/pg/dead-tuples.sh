#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"

section "Dead tuples"
query_table "
SELECT
  schemaname,
  relname AS table_name,
  n_live_tup,
  n_dead_tup,
  ROUND(
    100.0 * n_dead_tup /
    NULLIF(n_live_tup + n_dead_tup, 0), 2
  ) AS dead_ratio_pct,
  last_vacuum,
  last_autovacuum,
  last_analyze,
  last_autoanalyze
FROM pg_stat_user_tables
ORDER BY n_dead_tup DESC;
"
