#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"
section "Query statistics"
query_table "SELECT queryid,calls,round(total_exec_time::numeric,2) AS total_ms,round(mean_exec_time::numeric,2) AS mean_ms,round((shared_blks_hit::numeric*100)/NULLIF(shared_blks_hit+shared_blks_read,0),2) AS cache_hit_pct,rows,LEFT(query,180) AS query FROM pg_stat_statements ORDER BY calls DESC LIMIT 30;" || echo "pg_stat_statements is not available."
