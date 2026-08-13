#!/usr/bin/env bash
# 운영 환경 docker compose 기동 스크립트
# - 바인드 마운트 디렉터리 생성 및 postgres 소유권 설정
# - docker-compose-prod.yml 기반 전체 서비스 빌드·기동
set -euo pipefail

cd "$(dirname "$0")"

# 바인드 마운트 디렉터리 준비
# postgres(uid 999)가 쓸 수 있도록 소유권을 맞춰야 함
# (디렉터리가 없으면 docker가 root 소유로 생성 → postgres 기동 실패)
mkdir -p data/postgres data/redis logs
chown -R 999:999 data/postgres

docker compose -f docker-compose-prod.yml up -d --build
