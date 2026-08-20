#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/lib.sh"

section "Database XID age"

query_table "
SELECT
  datname,
  age(datfrozenxid) AS xid_age,
  pg_size_pretty(pg_database_size(datname)) AS database_size
FROM pg_database
WHERE datallowconn
ORDER BY age(datfrozenxid) DESC;
"

section "Table XID age"

query_table "
SELECT
  schemaname,
  relname,
  age(relfrozenxid) AS xid_age,
  n_live_tup,
  n_dead_tup,
  pg_size_pretty(pg_total_relation_size(relid)) AS total_size
FROM pg_stat_user_tables
ORDER BY age(relfrozenxid) DESC
LIMIT 50;
"
