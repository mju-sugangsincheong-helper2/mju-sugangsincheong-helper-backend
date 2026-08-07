#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

# 바인드 마운트 디렉터리 준비 (없으면 docker 가 root 소유로 만들고 postgres 가 못 씀)
mkdir -p data/postgres data/redis logs
# postgres 프로세스는 uid 999 로 실행되므로 소유권을 맞춘다 (root 권한 필요)
chown -R 999:999 data/postgres

docker compose -f docker-compose-prod.yml up -d --build
