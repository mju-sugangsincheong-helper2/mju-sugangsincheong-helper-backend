#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/lib.sh"

section "pg_stat_statements overview"

query_table "
SELECT
  count(*) AS normalized_queries,
  sum(calls) AS total_calls,
  ROUND(sum(total_exec_time)::numeric, 2) AS total_exec_ms,
  sum(shared_blks_hit) AS shared_hits,
  sum(shared_blks_read) AS shared_reads,
  ROUND(
    100.0 * sum(shared_blks_hit) /
    NULLIF(sum(shared_blks_hit) + sum(shared_blks_read), 0), 2
  ) AS cache_hit_pct
FROM pg_stat_statements;
"

section "Queries with most disk reads"

query_table "
SELECT
  calls,
  shared_blks_read,
  shared_blks_hit,
  ROUND(
    100.0 * shared_blks_hit /
    NULLIF(shared_blks_hit + shared_blks_read, 0), 2
  ) AS hit_pct,
  ROUND(mean_exec_time::numeric, 2) AS mean_ms,
  LEFT(regexp_replace(query, '[[:space:]]+', ' ', 'g'), 250) AS query
FROM pg_stat_statements
ORDER BY shared_blks_read DESC
LIMIT 30;
"
