#!/usr/bin/env bash
# tools/jfr-lib.sh — 일반 JFR 측정 메커니즘. 사례 스크립트에서 source로 불러 씀.
# 수정 금지. 계약은 같은 exp의 tools/CONTRACT.md. 바꿀 수 있는 건 호출 측 env뿐.
#
# 호출 측이 정하는 env (사례 의존성):
#   BASE_URL            예: http://localhost:8080
#   RAW                 산출물 디렉토리 (server.log, trace.jfr, trace.json)
#   HEALTH_URL          기본 $BASE_URL/actuator/health
#   BOOT_CMD            서버 기동 명령. 기본 "SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun"
#   BOOT_CWD            BOOT_CMD 실행 디렉토리. 기본 $PROJ_ROOT
#   TAG_HEADER          기본 X-JFR-TAG
#   JFR_EVENTS          jfr print 이벤트 목록. 기본값 아래 참조
#
# 제공 함수:
#   jfr_require_tools                       curl/jq/jcmd/jfr/uv 확인
#   jfr_server_up                           서버 없으면 기동, 있으면 패스
#   jfr_server_down                         jfr_server_up이 기동한 것만 종료
#   jfr_app_pid <pattern>                   jps로 PID 1개 출력
#   jfr_start <pid> [name] [settings]       기록 시작
#   jfr_dump <pid> <file> [name]            기록 dump (정지 아님)
#   jfr_stop <pid> [name]                   기록 정지 (실패 무시)
#   jfr_tagged_curl <tag> <out> <curl...>   태그 헤더付 curl (응답 out 저장)
#   jfr_dump_json <in.jfr> <out.json>       jfr → json

JFR_DEFAULT_EVENTS="http.server.request,jdk.ThreadStart,jdk.ExecutionSample,jdk.NativeMethodSample,jdk.SocketRead,jdk.SocketWrite,jdk.JavaMonitorWait,jdk.JavaMonitorEnter,jdk.ThreadPark,jdk.ThreadSleep,jdk.FileRead,jdk.FileWrite,jdk.JavaExceptionThrow"

jfr_require_tools() {
  command -v curl >/dev/null || { echo "curl 필요" >&2; return 1; }
  command -v jq >/dev/null || { echo "jq 필요" >&2; return 1; }
  command -v uv >/dev/null || { echo "uv 필요" >&2; return 1; }
  if ! command -v jcmd >/dev/null || ! command -v jfr >/dev/null; then
    if [ -x "/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home/bin/jcmd" ]; then
      export PATH="/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home/bin:$PATH"
    else
      echo "jcmd/jfr 필요" >&2; return 1
    fi
  fi
}

_JFR_GRADLE_PID=""
_JFR_STARTED_BY_ME=0

jfr_server_up() {
  local health="${HEALTH_URL:-$BASE_URL/actuator/health}"
  if curl -sS -o /dev/null -w "%{http_code}" "$health" 2>/dev/null | grep -q "200"; then
    return 0
  fi
  local cwd="${BOOT_CWD:-$PROJ_ROOT}"
  local cmd="${BOOT_CMD:-SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun}"
  mkdir -p "$RAW"
  (cd "$cwd" && sh -c "$cmd --console=plain" > "$RAW/server.log" 2>&1) &
  _JFR_GRADLE_PID=$!
  _JFR_STARTED_BY_ME=1
  for _i in $(seq 1 90); do
    if curl -sS -o /dev/null -w "%{http_code}" "$health" 2>/dev/null | grep -q "200"; then return 0; fi
    if ! kill -0 "$_JFR_GRADLE_PID" 2>/dev/null; then tail -n 50 "$RAW/server.log" >&2; return 1; fi
    sleep 2
    if [ "$_i" -eq 90 ]; then tail -n 50 "$RAW/server.log" >&2; return 1; fi
  done
}

jfr_server_down() {
  if [ "$_JFR_STARTED_BY_ME" -eq 1 ] && [ -n "$_JFR_GRADLE_PID" ] && kill -0 "$_JFR_GRADLE_PID" 2>/dev/null; then
    kill -TERM "$_JFR_GRADLE_PID" 2>/dev/null || true
    wait "$_JFR_GRADLE_PID" 2>/dev/null || true
  fi
  _JFR_GRADLE_PID=""
  _JFR_STARTED_BY_ME=0
}

jfr_app_pid() {
  jps -l 2>/dev/null | awk -v pat="$1" '$0 ~ pat {print $1; exit}'
}

jfr_start() {
  local pid="$1" name="${2:-tool}" settings="${3:-profile}"
  jcmd "$pid" JFR.stop name="$name" >/dev/null 2>&1 || true
  jcmd "$pid" JFR.start name="$name" settings="$settings"
}

jfr_dump() {
  local pid="$1" file="$2" name="${3:-tool}"
  jcmd "$pid" JFR.dump name="$name" filename="$file"
}

jfr_stop() {
  local pid="$1" name="${2:-tool}"
  jcmd "$pid" JFR.stop name="$name" >/dev/null 2>&1 || true
}

jfr_tagged_curl() {
  local tag="$1" out="$2"
  shift 2
  curl -sS -f --max-time 900 "$@" -H "${TAG_HEADER:-X-JFR-TAG}: $tag" -o "$out"
}

jfr_dump_json() {
  local in="$1" out="$2" events="${3:-$JFR_DEFAULT_EVENTS}"
  [ -f "$in" ] || { echo "$in 없음" >&2; return 1; }
  jfr print --json --stack-depth 256 --events "$events" "$in" > "$out.tmp"
  mv "$out.tmp" "$out"
}
