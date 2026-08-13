#!/usr/bin/env bash
# 개발 환경 docker compose 기동 스크립트
# - logs 디렉터리 생성
# - docker-compose-dev.yml 기반 전체 서비스 빌드·기동
set -euo pipefail

cd "$(dirname "$0")"

# 로그 디렉터리 생성 (application.yml에서 참조)
mkdir -p logs

docker compose -f docker-compose-dev.yml up -d --build
