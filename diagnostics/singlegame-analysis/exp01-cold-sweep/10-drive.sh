#!/usr/bin/env bash
set -euo pipefail
# 순차 1발씩. API 응답 파싱 없음. wall은 windows.tsv(start/end)만 사용.
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJ_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
RAW="$SCRIPT_DIR/raw"
HTTP="$RAW/http"
WIN="$RAW/windows.tsv"

command -v curl >/dev/null || { echo "curl 필요" >&2; exit 1; }
command -v jq >/dev/null || { echo "jq 필요" >&2; exit 1; }
mkdir -p "$HTTP" "$SCRIPT_DIR/report" "$PROJ_ROOT/build/jfr"
: > "$WIN"

GRADLE_PID=""
STARTED_BY_ME=0
cleanup() {
  if [ "$STARTED_BY_ME" -eq 1 ] && [ -n "$GRADLE_PID" ] && kill -0 "$GRADLE_PID" 2>/dev/null; then
    kill -TERM "$GRADLE_PID" 2>/dev/null || true
    wait "$GRADLE_PID" 2>/dev/null || true
  fi
}
trap cleanup EXIT INT TERM

if ! curl -sS -o /dev/null -w "%{http_code}" "$BASE_URL/actuator/health" 2>/dev/null | grep -q "200"; then
  cd "$PROJ_ROOT"
  SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun --console=plain > "$RAW/server.log" 2>&1 &
  GRADLE_PID=$!
  STARTED_BY_ME=1
  for i in $(seq 1 90); do
    if curl -sS -o /dev/null -w "%{http_code}" "$BASE_URL/actuator/health" 2>/dev/null | grep -q "200"; then break; fi
    if ! kill -0 "$GRADLE_PID" 2>/dev/null; then tail -n 50 "$RAW/server.log" >&2; exit 1; fi
    sleep 2
    if [ "$i" -eq 90 ]; then tail -n 50 "$RAW/server.log" >&2; exit 1; fi
  done
fi

login_resp="$(curl -sS -f -X POST "$BASE_URL/api/$API_VERSION/auth/test-login?name=$LOGIN_NAME")"
TOKEN="$(echo "$login_resp" | jq -r '.data.sessionAccessToken // empty')"
[ -n "$TOKEN" ] || { echo "로그인 실패" >&2; exit 1; }

curl -sS -f -X DELETE "$BASE_URL/api/$API_VERSION/diagnostics/singlegame/seed" \
  -H "Authorization: Bearer $TOKEN" > /dev/null

prev=0
i=0
for target in $STEPS; do
  i=$((i + 1))
  nnn=$(printf "%03d" "$i")
  label="$(echo "$LABELS" | cut -d' ' -f"$i")"
  delta=$((target - prev))
  curl -sS -f --max-time 900 -X POST \
    "$BASE_URL/api/$API_VERSION/diagnostics/singlegame/seed?count=$delta&totalCourses=$TOTAL_COURSES" \
    -H "Authorization: Bearer $TOKEN" > /dev/null
  out="$HTTP/${nnn}-${label}.json"
  start=$(date +%s.%N)
  curl -sS -f --max-time 900 -G \
    "$BASE_URL/api/$API_VERSION/diagnostics/singlegame/analysis/api" \
    --data-urlencode "totalCourses=$TOTAL_COURSES" \
    -H "Authorization: Bearer $TOKEN" -o "$out"
  end=$(date +%s.%N)
  printf "%s-%s\t%s\t%s\n" "$nnn" "$label" "$start" "$end" >> "$WIN"
  echo "저장: $out"
  sleep 2
  prev=$target
done

curl -sS -f -X DELETE "$BASE_URL/api/$API_VERSION/diagnostics/singlegame/seed" \
  -H "Authorization: Bearer $TOKEN" > /dev/null || true

if [ "$STARTED_BY_ME" -eq 1 ]; then
  kill -TERM "$GRADLE_PID" 2>/dev/null || true
  wait "$GRADLE_PID" 2>/dev/null || true
  GRADLE_PID=""
  STARTED_BY_ME=0
  sleep 5
fi
trap - EXIT INT TERM

JFR_SRC="$(ls -t "$PROJ_ROOT"/build/jfr/*.jfr 2>/dev/null | head -n 1 || true)"
if [ -n "${JFR_SRC:-}" ]; then
  cp "$JFR_SRC" "$RAW/trace.jfr"
  echo "JFR 복사: $JFR_SRC"
else
  echo "경고: build/jfr/*.jfr 없음" >&2
fi
