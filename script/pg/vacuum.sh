#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"

section "VACUUM / ANALYZE activity"
query_table "
SELECT
  schemaname,
  relname AS table_name,
  n_live_tup,
  n_dead_tup,
  last_vacuum,
  last_autovacuum,
  last_analyze,
  last_autoanalyze,
  vacuum_count,
  autovacuum_count,
  analyze_count,
  autoanalyze_count
FROM pg_stat_user_tables
ORDER BY n_dead_tup DESC;
"

section "Currently running VACUUM / ANALYZE"
query_table "
SELECT
  pid,
  datname,
  usename,
  state,
  now() - query_start AS duration,
  wait_event_type,
  wait_event,
  LEFT(query, 200) AS query
FROM pg_stat_activity
WHERE query ILIKE '%vacuum%'
   OR query ILIKE '%analyze%'
ORDER BY query_start;
"
