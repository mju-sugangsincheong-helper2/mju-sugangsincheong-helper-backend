#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"
section "Queue-related tables"
query_table "
SELECT schemaname, relname AS table_name,
       pg_size_pretty(pg_total_relation_size(relid)) AS total_size,
       n_live_tup, n_dead_tup, seq_scan, idx_scan,
       n_tup_ins, n_tup_upd, n_tup_del
FROM pg_stat_user_tables
WHERE relname ILIKE '%queue%' OR relname ILIKE '%job%' OR relname ILIKE '%message%'
ORDER BY pg_total_relation_size(relid) DESC;
"
section "PGMQ-related activity"
query_table "
SELECT pid, usename, state,
       now()-query_start AS query_age,
       LEFT(query,200) AS query
FROM pg_stat_activity
WHERE query ILIKE '%pgmq%'
ORDER BY query_start;
"
