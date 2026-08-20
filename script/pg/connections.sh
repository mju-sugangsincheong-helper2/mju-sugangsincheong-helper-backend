#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"
section "Connections"
query_table "SELECT usename,state,count(*) AS connections FROM pg_stat_activity WHERE datname=current_database() GROUP BY usename,state ORDER BY usename,state;"
query_table "SELECT current_setting('max_connections')::int AS max_connections,count(*) AS used_connections,current_setting('max_connections')::int-count(*) AS remaining_connections FROM pg_stat_activity;"
