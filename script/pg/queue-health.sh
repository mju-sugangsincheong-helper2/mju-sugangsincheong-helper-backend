#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"
section "Queue-related tables"
query_table "SELECT schemaname,relname AS table_name,n_live_tup,n_dead_tup,pg_size_pretty(pg_total_relation_size(relid)) AS total_size,last_autovacuum FROM pg_stat_user_tables WHERE relname ILIKE '%job%' OR relname ILIKE '%queue%' OR relname ILIKE '%reservation%' ORDER BY n_dead_tup DESC;"
