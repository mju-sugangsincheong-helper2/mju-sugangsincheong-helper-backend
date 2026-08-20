#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"

section "PostgreSQL health"
show_context
query "SELECT version();"
query_table "
SELECT
  current_database() AS database,
  pg_size_pretty(pg_database_size(current_database())) AS database_size,
  now() AS checked_at,
  current_setting('server_version') AS postgres_version;
"
query_table "
SELECT
  count(*) FILTER (WHERE state = 'active') AS active,
  count(*) FILTER (WHERE state = 'idle') AS idle,
  count(*) FILTER (WHERE state = 'idle in transaction') AS idle_in_transaction,
  count(*) AS total
FROM pg_stat_activity
WHERE datname = current_database();
"
