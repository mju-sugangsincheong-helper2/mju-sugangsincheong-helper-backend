#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/lib.sh"

section "User tables"

query_table "
SELECT
  schemaname,
  relname,
  n_live_tup,
  n_dead_tup,
  pg_size_pretty(pg_relation_size(relid)) AS table_size,
  pg_size_pretty(pg_indexes_size(relid)) AS index_size,
  pg_size_pretty(pg_total_relation_size(relid)) AS total_size,
  seq_scan,
  idx_scan
FROM pg_stat_user_tables
ORDER BY pg_total_relation_size(relid) DESC;
"
