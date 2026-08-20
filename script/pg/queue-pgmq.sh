#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"
section "PGMQ extension"
query_table "SELECT extname,extversion FROM pg_extension WHERE extname='pgmq';"
query_table "SELECT schemaname,tablename FROM pg_tables WHERE tablename LIKE 'pgmq%_queue' OR tablename LIKE 'pgmq%';"
