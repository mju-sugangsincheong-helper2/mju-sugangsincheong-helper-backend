#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"
section "pg_cron jobs"
query_table "SELECT jobid,jobname,schedule,command,active FROM cron.job ORDER BY jobid;" || echo "pg_cron is not available."
query_table "SELECT jobid,runid,status,return_message,start_time,end_time FROM cron.job_run_details ORDER BY start_time DESC LIMIT 30;" || true
