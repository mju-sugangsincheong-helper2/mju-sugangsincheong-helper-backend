#!/usr/bin/env bash
# 싱글게임 dept-sequence-stats 부하 스위프 (dev 전용)
#
# 누적 방식으로 5만 → 100만까지 5만 단위로 시드를 쌓으며 측정한다.
#   1. POST /api/{version}/auth/test-login 으로 로그인 (Bearer 토큰 확보)
#   2. DELETE /api/{version}/diagnostics/singlegame/seed 로 초기화 (깨끗한 출발)
#   3. 각 목표량마다 부족분만 POST seed 로 추가 → GET dept-sequence-stats 측정
#      → diagnostics/<N>m-response.json 에 원본 응답 저장 (5m, 10m, ..., 100m)
#   4. 종료 후 DELETE /api/{version}/diagnostics/singlegame/seed 로 시드 정리
#
# 사용법 (어디서 실행해도 됨, 스크립트 위치 기준 처리):
#   zsh diagnostics/singlegame/diagnostics-sweep.sh
#   BASE_URL=http://localhost:8080 LOGIN_NAME=TEST_GUEST zsh diagnostics/singlegame/diagnostics-sweep.sh
#
# 요구사항: curl, jq / 서버가 dev 프로파일로 기동 중일 것
set -euo pipefail

# API 버전. 서버 버저닝(version = "1+")과 결과 폴더(diagnostics/singlegame/<버전>/)에 함께 쓰인다.
# 버전이 바뀌면 여기만 수정하면 된다.
API_VERSION="${API_VERSION:-1}"

BASE_URL="${BASE_URL:-http://localhost:8080}"
LOGIN_NAME="${LOGIN_NAME:-TEST_GUEST}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
TOTAL_COURSES="${TOTAL_COURSES:-6}"
DEPARTMENT="${DEPARTMENT:-컴퓨터공학과}"
OUT_DIR="${OUT_DIR:-$SCRIPT_DIR/${API_VERSION}/response}"

command -v curl >/dev/null || { echo "curl이 필요합니다." >&2; exit 1; }
command -v jq >/dev/null || { echo "jq가 필요합니다." >&2; exit 1; }

mkdir -p "$OUT_DIR"

echo "== 1. 로그인 (POST /api/{version}/auth/test-login?name=${LOGIN_NAME})"
login_resp="$(curl -sS -f -X POST "${BASE_URL}/api/${API_VERSION}/auth/test-login?name=${LOGIN_NAME}")"
TOKEN="$(echo "$login_resp" | jq -r '.data.sessionAccessToken // empty')"
if [ -z "$TOKEN" ]; then
  echo "로그인 실패: sessionAccessToken 없음" >&2
  echo "$login_resp" | head -c 500 >&2
  exit 1
fi
echo "로그인 성공 (token ${#TOKEN}자)"

echo "== 2. 시드 초기화 (DELETE /api/{version}/diagnostics/singlegame/seed)"
curl -sS -f -X DELETE "${BASE_URL}/api/${API_VERSION}/diagnostics/singlegame/seed" \
  -H "Authorization: Bearer ${TOKEN}" | jq '{deletedGames: .data.deletedGames, deletedDetails: .data.deletedDetails, deletedMembers: .data.deletedMembers}'

prev=0
for target in 50000 100000 150000 200000 250000 300000 350000 400000 450000 500000 550000 600000 650000 700000 750000 800000 850000 900000 950000 1000000; do
  label="$((target / 10000))m"
  delta=$((target - prev))
  out="${OUT_DIR}/${label}-response.json"

  echo "== [${label} / 누적 ${target}] 시드 +${delta} (POST seed?count=${delta}&totalCourses=${TOTAL_COURSES})"
  curl -sS -f --max-time 900 -X POST \
    "${BASE_URL}/api/${API_VERSION}/diagnostics/singlegame/seed?count=${delta}&totalCourses=${TOTAL_COURSES}" \
    -H "Authorization: Bearer ${TOKEN}" | jq '{games: .data.games, details: .data.details, elapsedMs: .data.elapsedMs}'

  echo "== [${label}] 측정 (GET dept-sequence-stats, ${DEPARTMENT} 1회)"
  curl -sS -f --max-time 900 -G \
    "${BASE_URL}/api/${API_VERSION}/diagnostics/singlegame/dept-sequence-stats" \
    --data-urlencode "totalCourses=${TOTAL_COURSES}" \
    --data-urlencode "department=${DEPARTMENT}" \
    -H "Authorization: Bearer ${TOKEN}" -o "$out"
  ms="$(jq -r '.data.ms' "$out")"
  echo "저장: $out (ms=${ms})"

  prev=$target
done

echo
echo "== 요약 (누적 게임 수 / ms)"
printf "%-8s %12s\n" "label" "ms"
for f in "$OUT_DIR"/*-response.json; do
  jq -r '"\(input_filename | split("/")[-1] | split("-")[0]) \(.data.ms)"' "$f" \
    | awk '{printf "%-8s %12.2f\n", $1, $2}'
done | sort -n

echo "== 4. 시드 정리 (DELETE /api/{version}/diagnostics/singlegame/seed)"
curl -sS -f -X DELETE "${BASE_URL}/api/${API_VERSION}/diagnostics/singlegame/seed" \
  -H "Authorization: Bearer ${TOKEN}" | jq '{deletedGames: .data.deletedGames, deletedDetails: .data.deletedDetails, deletedMembers: .data.deletedMembers}'
echo "완료."
