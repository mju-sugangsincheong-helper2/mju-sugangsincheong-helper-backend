#!/bin/bash

# 변수 설정 (설정하신 스크립트 기준)
DB_CONTAINER_NAME="mjusugangsincheonghelpercontainer_postgres"
DB_ROOT_USER="postgres"
DB_NAME="mjusugangsincheonghelperdb"

echo "========================================="
echo "🔍 PostgreSQL 확장 기능 검증 시작"
echo "========================================="

# 공통 실행 함수
run_sql() {
    docker exec -i "${DB_CONTAINER_NAME}" psql -U "${DB_ROOT_USER}" -d "${DB_NAME}" -c "$1"
}

# --------------------------------------------------
# Test 1: pg_stat_statements (쿼리 성능 통계)
# --------------------------------------------------
echo -e "\n-----------------------------------------"
echo "📊 1. pg_stat_statements 테스트"
echo "-----------------------------------------"
# 임의의 쿼리를 실행하여 통계 수집을 유도합니다.
echo "테스트용 임의 쿼리 실행..."
run_sql "SELECT 1 + 1 AS test_calc;" > /dev/null

echo "통계 뷰 조회 시도..."
# 통계 테이블에서 방금 전 실행한 쿼리와 통계 데이터가 잡히는지 확인합니다.
run_sql "SELECT query, calls, total_exec_time FROM pg_stat_statements ORDER BY total_exec_time DESC LIMIT 3;"

# --------------------------------------------------
# Test 2: pgmq (메시지 큐 확장 기능)
# --------------------------------------------------
echo -e "\n-----------------------------------------"
echo "✉️ 2. pgmq (Postgres Message Queue) 테스트"
echo "-----------------------------------------"
echo "1) 테스트 큐(test_queue) 생성..."
run_sql "SELECT pgmq.create('test_queue');"

echo "2) 메시지 발송 (Enqueue)..."
run_sql "SELECT pgmq.send('test_queue', '{\"user_id\": 123, \"action\": \"signup\"}');"

echo "3) 메시지 읽기 (Read/Dequeue)..."
# 10초 동안 보이지 않게 처리(visibility timeout), 1개 조회
run_sql "SELECT msg_id, read_ct, message FROM pgmq.read('test_queue', 10, 1);"

echo "4) 메시지 아카이브 (보관 후 원본 삭제)..."
run_sql "SELECT pgmq.archive('test_queue', 1);"

echo "5) 테스트 큐 제거..."
run_sql "SELECT pgmq.drop_queue('test_queue');"

# --------------------------------------------------
# Test 3: pg_cron (작업 스케줄러)
# --------------------------------------------------
echo -e "\n-----------------------------------------"
echo "⏰ 3. pg_cron (크론 스케줄러) 테스트"
echo "-----------------------------------------"
echo "1) 테스트용 더미 테이블 생성..."
run_sql "CREATE TABLE IF NOT EXISTS cron_test_table (id serial, msg text, created_at timestamp default now());"

echo "2) 1분 주기 스케줄 작업 등록..."
# pg_cron은 cron.schedule 함수를 사용합니다.
run_sql "SELECT cron.schedule('test-cron-job', '* * * * *', 'INSERT INTO cron_test_table (msg) VALUES (''cron_tested'')');"

echo "3) 등록된 크론 작업 확인..."
run_sql "SELECT jobid, jobname, schedule, command, database FROM cron.job WHERE jobname = 'test-cron-job';"

echo "4) 스케줄 작업 해제 및 더미 테이블 삭제 (정리)..."
run_sql "SELECT cron.unschedule('test-cron-job');"
run_sql "DROP TABLE cron_test_table;"

echo -e "\n========================================="
echo "✅ 테스트 스크립트 수행 완료"
echo "========================================="
