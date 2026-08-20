#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"
section "Tables"
query_table "SELECT schemaname,relname AS table_name,pg_size_pretty(pg_total_relation_size(relid)) AS total_size,pg_size_pretty(pg_table_size(relid)) AS table_size,pg_size_pretty(pg_indexes_size(relid)) AS indexes,n_live_tup,n_dead_tup FROM pg_stat_user_tables ORDER BY pg_total_relation_size(relid) DESC;"
