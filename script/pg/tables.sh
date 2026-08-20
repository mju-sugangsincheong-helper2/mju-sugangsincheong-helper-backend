#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"

section "Tables"
query_table "
SELECT
  schemaname,
  relname AS table_name,
  pg_size_pretty(pg_total_relation_size(relid)) AS total_size,
  n_live_tup,
  n_dead_tup,
  seq_scan,
  idx_scan,
  n_tup_ins,
  n_tup_upd,
  n_tup_del,
  n_tup_hot_upd
FROM pg_stat_user_tables
ORDER BY pg_total_relation_size(relid) DESC;
"
