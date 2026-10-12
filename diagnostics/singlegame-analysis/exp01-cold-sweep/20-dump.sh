#!/usr/bin/env bash
set -euo pipefail
# trace.jfr → trace.json 변환. 1회 실행, 항상 전체 재생성(이어쓰기 금지).
# 30-analyze.py는 이 파일을 N회 읽는다.
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
RAW="$SCRIPT_DIR/raw"
EVENTS="jdk.ExecutionSample,jdk.NativeMethodSample,jdk.SocketRead,jdk.SocketWrite,jdk.JavaMonitorWait,jdk.JavaMonitorEnter,jdk.ThreadPark,jdk.ThreadSleep,jdk.FileRead,jdk.FileWrite,jdk.JavaExceptionThrow"

[ -f "$RAW/trace.jfr" ] || { echo "raw/trace.jfr 없음" >&2; exit 1; }
command -v jfr >/dev/null || { echo "jfr CLI 없음" >&2; exit 1; }

jfr print --json --stack-depth 256 --events "$EVENTS" "$RAW/trace.jfr" > "$RAW/trace.json.tmp"
mv "$RAW/trace.json.tmp" "$RAW/trace.json"
echo "저장: $RAW/trace.json"
