#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"
section "Slow queries (pg_stat_statements)"
query_table "
SELECT calls,
       ROUND(total_exec_time::numeric,2) AS total_ms,
       ROUND(mean_exec_time::numeric,2) AS mean_ms,
       rows, LEFT(query,220) AS query
FROM pg_stat_statements
ORDER BY mean_exec_time DESC
LIMIT 30;
"
