#!/bin/bash
# manage.sh
# 로컬 개발/테스트 통합 인프라 제어 스크립트

# -------------------------------------------------------------
# [설정 정의 구역] - 모든 관리 포인트를 상단에서 제어합니다.
# -------------------------------------------------------------
COMPOSE_FILE="docker-compose-dev-test.yml"
DATA_DIR="./data"

# 1. PostgreSQL 시스템 관리 계정 (마스터 계정)
POSTGRES_DB="postgres"
POSTGRES_USER="postgres"

# 2. 통합 일반 사용자 계정 (단일화)
DB_USER="mjusugangsincheonghelperuser"
DB_PASSWORD="mjusugangsincheonghelperpassword"

# 3. 데이터베이스 분리 설정
DEV_DB_NAME="mjusugangsincheonghelperdb_dev"
TEST_DB_NAME="mjusugangsincheonghelperdb_test"

# 4. 컨테이너 식별 이름 정의
DB_CONTAINER="mjusugangsincheonghelpercontainer_postgres"
PGADMIN_CONTAINER="mjusugangsincheonghelper_pgadmin"
REDIS_CONTAINER="mjusugangsincheonghelper_redis"
REDIS_COMMANDER_CONTAINER="mjusugangsincheonghelper_redis_commander"

# -------------------------------------------------------------
# [로직 구역]
# -------------------------------------------------------------
usage() {
    echo "사용법: $0 {start|stop|clean|clean-start}"
    exit 1
}

if [ -z "$1" ]; then
    usage
fi

safe_remove_data() {
    echo "🛑 안전을 위해 실행 중인 컨테이너 정화 및 강제 종료를 수행합니다..."
    docker rm -f "$DB_CONTAINER" "$PGADMIN_CONTAINER" "$REDIS_CONTAINER" "$REDIS_COMMANDER_CONTAINER" 2>/dev/null

    if [ -d "$DATA_DIR" ]; then
        echo "🧹 임시 컨테이너 권한으로 데이터 디렉터리를 완전 제거합니다..."
        docker run --rm -v "$(pwd)/data:/temp_data" alpine sh -c "rm -rf /temp_data/postgres /temp_data/redis" 2>/dev/null
        rm -rf "$DATA_DIR" 2>/dev/null || true
    fi
}

initialize_databases() {
    echo "⌛ PostgreSQL 시작 대기 중..."
    until docker exec "$DB_CONTAINER" pg_isready -U "$POSTGRES_USER" > /dev/null 2>&1; do
      echo "접속 대기 중... (1초)"
      sleep 1
    done

    echo "🛠️ 개발용 및 테스트용 데이터베이스 구성 중..."

    # 통합 유저 생성 및 데이터베이스 생성 DDL
    docker exec -i "$DB_CONTAINER" psql -U "$POSTGRES_USER" <<EOF
-- 1. 통합 일반 유저 생성
DO \$\$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = '$DB_USER') THEN
    CREATE ROLE $DB_USER WITH LOGIN PASSWORD '$DB_PASSWORD' SUPERUSER;
  END IF;
END
\$\$;

-- 2. Dev 및 Test 데이터베이스 각각 생성 (통합 일반 유저 소유)
SELECT 'CREATE DATABASE $DEV_DB_NAME OWNER $DB_USER'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = '$DEV_DB_NAME')\gexec

SELECT 'CREATE DATABASE $TEST_DB_NAME OWNER $DB_USER'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = '$TEST_DB_NAME')\gexec

-- 해당 DB들과 일반 유저에 search_path 설정을 영구적으로 강제 주입합니다.
ALTER DATABASE $DEV_DB_NAME SET search_path TO public;
ALTER DATABASE $TEST_DB_NAME SET search_path TO public;
ALTER ROLE $DB_USER SET search_path TO public, '$user';
EOF

    echo "🧩 PostgreSQL 확장 기능(pg_cron, pgmq, pg_stat_statements) 활성화 중..."

    # 3. Dev DB 확장 기능 탑재
    docker exec -i "$DB_CONTAINER" psql -U "$POSTGRES_USER" -d "$DEV_DB_NAME" <<EOF
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;
CREATE EXTENSION IF NOT EXISTS pg_cron;
CREATE EXTENSION IF NOT EXISTS pgmq;
CREATE EXTENSION IF NOT EXISTS system_stats;
EOF

    # 4. Test DB 확장 기능 탑재 (pg_cron 제외)
    docker exec -i "$DB_CONTAINER" psql -U "$POSTGRES_USER" -d "$TEST_DB_NAME" <<EOF
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;
CREATE EXTENSION IF NOT EXISTS pgmq;
EOF

    echo "🎉 모든 데이터베이스 설정이 완료되었습니다!"
}

case "$1" in
    start)
        echo "🚀 로컬 통합 인프라 가동 중..."
        mkdir -p "$DATA_DIR/postgres" "$DATA_DIR/redis"
        chmod 777 "$DATA_DIR/postgres" "$DATA_DIR/redis" 2>/dev/null || true

        docker compose -f "$COMPOSE_FILE" up -d --build --remove-orphans
        initialize_databases
        ;;

    stop)
        echo "🛑 로컬 통합 컨테이너 정지 중 (데이터 유지)..."
        docker compose -f "$COMPOSE_FILE" down --remove-orphans
        ;;

    clean)
        echo "🧹 로컬 통합 컨테이너 정지 및 데이터 볼륨 완전 삭제 중..."
        docker compose -f "$COMPOSE_FILE" down -v --remove-orphans 2>/dev/null
        safe_remove_data
        echo "✅ 로컬 환경 데이터가 물리적으로 모두 삭제되었습니다."
        ;;

    clean-start)
        echo "✨ 로컬 환경 완전 초기화 후 재시작 중..."
        docker compose -f "$COMPOSE_FILE" down -v --remove-orphans 2>/dev/null
        safe_remove_data

        mkdir -p "$DATA_DIR/postgres" "$DATA_DIR/redis"
        chmod 777 "$DATA_DIR/postgres" "$DATA_DIR/redis" 2>/dev/null || true

        docker compose -f "$COMPOSE_FILE" up -d --build --remove-orphans
        initialize_databases
        ;;

    *)
        usage
        ;;
esac
