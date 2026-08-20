#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/lib.sh"

section "Replication clients"

query_table "
SELECT
  pid,
  usename,
  application_name,
  client_addr,
  state,
  sync_state,
  sent_lsn,
  write_lsn,
  flush_lsn,
  replay_lsn,
  write_lag,
  flush_lag,
  replay_lag
FROM pg_stat_replication
ORDER BY pid;
"

section "Replication slots"

query_table "
SELECT
  slot_name,
  slot_type,
  active,
  restart_lsn,
  confirmed_flush_lsn,
  wal_status,
  safe_wal_size,
  temporary
FROM pg_replication_slots
ORDER BY slot_name;
"

section "WAL"

query_table "
SELECT
  pg_current_wal_lsn() AS current_wal_lsn,
  pg_size_pretty(pg_wal_lsn_diff(pg_current_wal_lsn(), '0/0')) AS current_lsn_offset;
"
