#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/lib.sh"

section "VACUUM / ANALYZE statistics"

query_table "
SELECT
  schemaname,
  relname,
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

section "Currently running VACUUM"

query_table "
SELECT
  pid,
  datname,
  usename,
  state,
  now() - query_start AS duration,
  wait_event_type,
  wait_event,
  LEFT(regexp_replace(query, '[[:space:]]+', ' ', 'g'), 250) AS query
FROM pg_stat_activity
WHERE query ILIKE '%vacuum%'
  AND pid <> pg_backend_pid()
ORDER BY query_start;
"

section "Autovacuum settings"

query "
SELECT name, setting, unit
FROM pg_settings
WHERE name IN (
  'autovacuum',
  'autovacuum_naptime',
  'autovacuum_vacuum_threshold',
  'autovacuum_vacuum_scale_factor',
  'autovacuum_analyze_threshold',
  'autovacuum_analyze_scale_factor',
  'autovacuum_vacuum_cost_delay',
  'autovacuum_vacuum_cost_limit',
  'vacuum_cost_delay',
  'vacuum_cost_limit'
)
ORDER BY name;
"
