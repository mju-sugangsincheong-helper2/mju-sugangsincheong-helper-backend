#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"
section "VACUUM / ANALYZE status"
query_table "SELECT schemaname,relname AS table_name,n_live_tup,n_dead_tup,last_vacuum,last_autovacuum,last_analyze,last_autoanalyze FROM pg_stat_user_tables ORDER BY n_dead_tup DESC;"
query_table "SELECT pid,relid::regclass AS relation,phase,heap_blks_total,heap_blks_scanned,heap_blks_vacuumed,index_vacuum_count FROM pg_stat_progress_vacuum ORDER BY pid;"
