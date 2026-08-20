#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/lib.sh"

echo "PostgreSQL production health"
show_context

section "Server"
query_table "
SELECT
  current_database() AS database,
  current_user AS user,
  version() AS version,
  now() AS checked_at;
"

section "Connections"
query_table "
SELECT
  count(*) FILTER (WHERE state = 'active') AS active,
  count(*) FILTER (WHERE state = 'idle') AS idle,
  count(*) FILTER (WHERE state = 'idle in transaction') AS idle_in_transaction,
  count(*) AS total,
  current_setting('max_connections')::int AS max_connections,
  ROUND(
    100.0 * count(*) / current_setting('max_connections')::int, 1
  ) AS connection_usage_pct
FROM pg_stat_activity;
"

section "Locks"
query_table "
SELECT count(*) AS blocked_sessions
FROM pg_stat_activity
WHERE cardinality(pg_blocking_pids(pid)) > 0;
"

section "Dead tuples"
query_table "
SELECT
  COALESCE(sum(n_live_tup), 0) AS live_tuples,
  COALESCE(sum(n_dead_tup), 0) AS dead_tuples,
  ROUND(
    100.0 * COALESCE(sum(n_dead_tup), 0) /
    NULLIF(sum(n_live_tup) + sum(n_dead_tup), 0), 2
  ) AS dead_ratio_pct
FROM pg_stat_user_tables;
"

section "Database size"
query_table "
SELECT pg_size_pretty(pg_database_size(current_database())) AS database_size;
"

section "Oldest active transaction"
query_table "
SELECT
  pid,
  usename,
  state,
  now() - xact_start AS transaction_age,
  backend_xmin,
  LEFT(regexp_replace(query, '[[:space:]]+', ' ', 'g'), 180) AS query
FROM pg_stat_activity
WHERE xact_start IS NOT NULL
ORDER BY xact_start
LIMIT 1;
"
