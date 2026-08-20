#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"
section "PGMQ extension"
query_table "SELECT extname, extversion FROM pg_extension WHERE extname='pgmq';"
section "PGMQ queues"
query_table "SELECT * FROM pgmq.list_queues() ORDER BY queue_name;"
