#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"

section "XID age / wraparound risk"
query_table "
SELECT
  datname,
  age(datfrozenxid) AS xid_age,
  pg_size_pretty(pg_database_size(datname)) AS database_size
FROM pg_database
ORDER BY age(datfrozenxid) DESC;
"

query_table "
SELECT
  schemaname,
  relname AS table_name,
  age(relfrozenxid) AS xid_age,
  n_live_tup,
  n_dead_tup
FROM pg_stat_user_tables
ORDER BY age(relfrozenxid) DESC
LIMIT 30;
"
