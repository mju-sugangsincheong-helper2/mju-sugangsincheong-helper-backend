#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"
section "XID age / wraparound risk"
query_table "SELECT datname,age(datfrozenxid) AS xid_age,current_setting('autovacuum_freeze_max_age')::bigint AS freeze_max_age,ROUND(100.0*age(datfrozenxid)/current_setting('autovacuum_freeze_max_age')::numeric,2) AS freeze_pct FROM pg_database WHERE datallowconn ORDER BY age(datfrozenxid) DESC;"
query_table "SELECT n.nspname AS schema_name,c.relname,age(c.relfrozenxid) AS xid_age FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE c.relkind IN ('r','m') ORDER BY age(c.relfrozenxid) DESC LIMIT 30;"
