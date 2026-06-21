#!/bin/bash

# 파일 디스크립터 제한 상향 (고동시성 연결 대응)
ulimit -n 65535 2>/dev/null

# VU_MAX 기본값 설정 (100,000 VU)
VU_MAX=${VU_MAX:-4000}

echo "================================================================="
echo " Starting MAX load test with VU_MAX = $VU_MAX"
echo " (OS File Descriptor limit set to max)"
echo "================================================================="

mkdir -p k6/report

# 과부하 테스트에 적합한 주요 스파이크/스트레스 테스트만 실행 (엔듀어런스 제외)
for f in k6/script/singlegame/spike-test.js k6/script/exchange/polling-storm.js; do
  echo "-----------------------------------------------------------------"
  echo " Running: $(basename "$f")"
  echo "-----------------------------------------------------------------"
  
  k6 run "$f" \
    -e BASE_URL=http://localhost:8080 \
    -e VU_MAX=$VU_MAX \
    --out json="k6/report/max_$(basename "$f" .js).json"
done

echo "================================================================="
echo " Max load test completed."
echo " Reports generated in k6/report/max_*.json"
echo "================================================================="
