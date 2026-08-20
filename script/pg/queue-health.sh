#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/lib.sh"

# This script is intentionally schema-aware only through pg_catalog.
# It reports tables whose names suggest queue/job semantics, without
# assuming a project-specific queue schema.

section "Likely queue/job tables"

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
  pg_size_pretty(pg_total_relation_size(relid)) AS total_size,
  last_autovacuum
FROM pg_stat_user_tables
WHERE relname ~* '(queue|job|message|outbox|event)'
ORDER BY n_dead_tup DESC, relname;
"

section "Likely queue/job indexes"

query_table "
SELECT
  schemaname,
  relname AS table_name,
  indexrelname AS index_name,
  pg_size_pretty(pg_relation_size(indexrelid)) AS index_size,
  idx_scan
FROM pg_stat_user_indexes
WHERE relname ~* '(queue|job|message|outbox|event)'
ORDER BY pg_relation_size(indexrelid) DESC;
"

section "Current workers touching likely queue tables"

query_table "
SELECT
  pid,
  usename,
  state,
  now() - xact_start AS xact_age,
  now() - query_start AS query_age,
  wait_event_type,
  wait_event,
  LEFT(regexp_replace(query, '[[:space:]]+', ' ', 'g'), 250) AS query
FROM pg_stat_activity
WHERE query ~* '(queue|job|message|outbox|event)'
ORDER BY query_start;
"
