#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"
section "Query statistics"
query_table "
SELECT calls,
       ROUND(total_exec_time::numeric,2) AS total_ms,
       ROUND((total_exec_time/NULLIF(calls,0))::numeric,2) AS avg_ms,
       ROUND((shared_blks_hit::numeric/NULLIF(shared_blks_hit+shared_blks_read,0)*100),2) AS cache_hit_pct,
       shared_blks_read, shared_blks_hit,
       temp_blks_read, temp_blks_written,
       LEFT(query,180) AS query
FROM pg_stat_statements
ORDER BY total_exec_time DESC
LIMIT 30;
"
