#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"
section "pg_cron jobs"
query_table "SELECT jobid, schedule, active, database, username, command FROM cron.job ORDER BY jobid;"
section "pg_cron recent run details"
query_table "SELECT jobid, runid, status, return_message, start_time, end_time FROM cron.job_run_details ORDER BY start_time DESC LIMIT 30;"
