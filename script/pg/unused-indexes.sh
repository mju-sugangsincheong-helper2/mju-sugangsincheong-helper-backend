#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/lib.sh"

section "Indexes with zero scans"

query_table "
SELECT
  schemaname,
  relname AS table_name,
  indexrelname AS index_name,
  pg_size_pretty(pg_relation_size(indexrelid)) AS size,
  idx_scan
FROM pg_stat_user_indexes
WHERE idx_scan = 0
  AND NOT indisunique
  AND NOT indisprimary
ORDER BY pg_relation_size(indexrelid) DESC;
"

warn "idx_scan=0 is only a candidate signal; do not drop an index from this output alone."
