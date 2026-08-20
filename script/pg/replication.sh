#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"
section "Replication"
query_table "SELECT pid,usename,application_name,client_addr,state,sync_state,write_lsn,flush_lsn,replay_lsn,write_lag,flush_lag,replay_lag FROM pg_stat_replication ORDER BY pid;"
query_table "SELECT slot_name,slot_type,active,pg_size_pretty(pg_wal_lsn_diff(pg_current_wal_lsn(),restart_lsn)) AS retained_wal,wal_status FROM pg_replication_slots ORDER BY slot_name;"
