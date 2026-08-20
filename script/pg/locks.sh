#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"
section "Locks / blocking"
query_table "SELECT blocked.pid AS blocked_pid,blocked.usename AS blocked_user,now()-blocked.query_start AS blocked_for,blocking.pid AS blocking_pid,blocking.usename AS blocking_user,now()-blocking.query_start AS blocking_for,LEFT(blocked.query,140) AS blocked_query,LEFT(blocking.query,140) AS blocking_query FROM pg_stat_activity blocked JOIN LATERAL unnest(pg_blocking_pids(blocked.pid)) b(pid) ON true JOIN pg_stat_activity blocking ON blocking.pid=b.pid WHERE blocked.datname=current_database() ORDER BY blocked.query_start;"
