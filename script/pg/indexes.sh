#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"

section "Indexes"
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
ORDER BY pg_relation_size(indexrelid) DESC;
"
