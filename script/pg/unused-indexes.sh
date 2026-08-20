#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"

section "Potentially unused indexes"
query_table "
SELECT
  schemaname,
  relname AS table_name,
  indexrelname AS index_name,
  pg_size_pretty(pg_relation_size(indexrelid)) AS index_size,
  idx_scan
FROM pg_stat_user_indexes
WHERE idx_scan = 0
  AND NOT indisunique
ORDER BY pg_relation_size(indexrelid) DESC;
"
