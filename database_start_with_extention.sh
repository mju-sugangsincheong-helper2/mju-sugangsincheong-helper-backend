#!/bin/bash

# 1. 변수 설정 (관리 포인트)
DB_ROOT_USER="postgres"
DB_ROOT_PW="postgrespassword"
DB_NAME="mjusugangsincheonghelperdb"
DB_USER="mjusugangsincheonghelperuser"
DB_PW="mjusugangsincheonghelperpassword"

PGADMIN_EMAIL="admin@admin.com"
PGADMIN_PW="1253"
PGADMIN_PORT="8005"

REDIS_PORT="6379"

REDIS_COMMANDER_PORT="8006"

DB_CONTAINER_NAME="mjusugangsincheonghelpercontainer_postgres"
PGADMIN_CONTAINER_NAME="mjusugangsincheonghelper_pgadmin"
REDIS_CONTAINER_NAME="mjusugangsincheonghelper_redis"
REDIS_COMMANDER_CONTAINER_NAME="mjusugangsincheonghelper_redis_commander"

# 파일명 변수화
COMPOSE_FILE="docker-compose-dev-env.yml"
DOCKERFILE_NAME="Postgres.Dockerfile"

# 스크립트 종료 시 생성된 파일 정리
cleanup() {
    echo "🧹 생성된 설정 파일 정리 중..."
    rm -f "$COMPOSE_FILE" "$DOCKERFILE_NAME" servers.json
    echo "✅ 정리 완료"
}
trap cleanup EXIT


# 2. 커스텀 PostgreSQL Dockerfile 생성
# (공식 postgres:17 이미지에 pg_cron 및 공식 최신 pgmq를 빌드하여 주입합니다.)
if [ ! -f "$DOCKERFILE_NAME" ]; then
    echo "📄 $DOCKERFILE_NAME 파일을 생성합니다..."
    cat <<EOF > "$DOCKERFILE_NAME"
FROM postgres:17

USER root

ENV DEBIAN_FRONTEND=noninteractive

# 1) pg_cron 및 pgmq 빌드를 위한 패키지 설치
RUN apt-get update && apt-get install -y --no-install-recommends \\
    postgresql-17-cron \\
    postgresql-server-dev-17 \\
    make \\
    git \\
    ca-certificates \\
    && rm -rf /var/lib/apt/lists/*

# 2) 공식 pgmq 최신 저장소 복제 후 설치 실행
RUN git clone https://github.com/pgmq/pgmq.git /tmp/pgmq \\
    && cd /tmp/pgmq/pgmq-extension \\
    && make install \\
    && rm -rf /tmp/pgmq

# 3) 빌드 도구 정리 (컨테이너 경량화)
RUN apt-get purge -y --auto-remove git make postgresql-server-dev-17 \\
    && rm -rf /var/lib/apt/lists/*

# 볼륨 권한 문제를 해결하기 위해 엔트리포인트를 root 계정으로 유지합니다.
# (내부 스크립트가 로컬 볼륨 권한 복구 후 DB 데몬은 안전하게 postgres 유저로 실행합니다.)
USER root
EOF
else
    echo "✅ 이미 $DOCKERFILE_NAME 파일이 존재합니다."
fi


# 3. docker-compose yml 파일 생성
if [ ! -f "$COMPOSE_FILE" ]; then
    echo "📄 $COMPOSE_FILE 파일이 없어 새로 생성합니다..."
    cat <<EOF > "$COMPOSE_FILE"
services:
  db:
    build:
      context: .
      dockerfile: ${DOCKERFILE_NAME}
    container_name: ${DB_CONTAINER_NAME}
    environment:
      POSTGRES_USER: ${DB_ROOT_USER}
      POSTGRES_PASSWORD: ${DB_ROOT_PW}
    ports:
      - "5432:5432"
    volumes:
      - ./data/postgres:/var/lib/postgresql/data
    command: >
      postgres
      -c shared_preload_libraries=pg_stat_statements,pg_cron
      -c cron.database_name=${DB_NAME}
    restart: always

  pgadmin:
    image: dpage/pgadmin4
    container_name: ${PGADMIN_CONTAINER_NAME}
    environment:
      PGADMIN_DEFAULT_EMAIL: ${PGADMIN_EMAIL}
      PGADMIN_DEFAULT_PASSWORD: ${PGADMIN_PW}
    ports:
      - "${PGADMIN_PORT}:80"
    volumes:
      - ./servers.json:/pgadmin4/servers.json
    depends_on:
      - db
    restart: always

  redis:
    image: redis:7-alpine
    container_name: ${REDIS_CONTAINER_NAME}
    ports:
      - "${REDIS_PORT}:6379"
    volumes:
      - ./data/redis:/data
    restart: always

  redis-commander:
    image: ghcr.io/joeferner/redis-commander:latest
    platform: linux/amd64
    container_name: ${REDIS_COMMANDER_CONTAINER_NAME}
    environment:
      REDIS_HOSTS: "local:redis:6379"
    ports:
      - "${REDIS_COMMANDER_PORT}:8081"
    depends_on:
      - redis
    restart: always
EOF
else
    echo "✅ 이미 $COMPOSE_FILE 파일이 존재합니다."
fi

# 4. pgAdmin 서버 등록 정보 파일 생성
if [ -d "servers.json" ]; then
    echo "⚠️ servers.json이 디렉터리로 잘못 생성되어 있어 삭제 후 파일로 재생성합니다..."
    rm -rf "servers.json"
fi

if [ ! -f "servers.json" ]; then
    echo "📄 servers.json 파일을 생성합니다..."
    cat <<EOF > servers.json
{
  "Servers": {
    "1": {
      "Name": "${DB_NAME}",
      "Group": "Servers",
      "Host": "db",
      "Port": 5432,
      "MaintenanceDB": "${DB_NAME}",
      "Username": "${DB_USER}",
      "Password": "${DB_PW}",
      "SSLMode": "prefer",
      "PassFile": "/tmp/pgpassfile"
    }
  }
}
EOF
else
    echo "✅ 이미 servers.json 파일이 존재합니다."
fi

# 5. 컨테이너 빌드 및 실행

echo "📁 마운트용 데이터 디렉터리를 미리 생성합니다..."
mkdir -p ./data/postgres ./data/redis
echo "🐳 Docker 컨테이너 빌드 및 실행 중..."
docker compose -f "$COMPOSE_FILE" up -d --build

# 6. DB 엔진 준비 대기 (상태 체크 루프)
echo "⌛ PostgreSQL 시작 대기 중..."
until docker exec ${DB_CONTAINER_NAME} pg_isready -U ${DB_ROOT_USER} > /dev/null 2>&1; do
  echo "접속 대기 중... (1초)"
  sleep 1
done

# pgAdmin 시작 및 servers.json 로드 대기
echo "⌛ pgAdmin 시작 및 servers.json 로딩 대기 중..."
until docker logs ${PGADMIN_CONTAINER_NAME} 2>&1 | grep -q "Listening at"; do
  echo "pgAdmin 로딩 대기 중... (1초)"
  sleep 1
done


# 7. 유저 및 데이터베이스 생성
echo "🛠️ 작업 유저(${DB_USER}) 및 데이터베이스 생성 중..."

# 유저 생성
docker exec -i ${DB_CONTAINER_NAME} psql -U ${DB_ROOT_USER} -c "CREATE USER ${DB_USER} WITH PASSWORD '${DB_PW}';" 2>/dev/null || echo "이미 존재하는 유저입니다."
# 데이터베이스 생성
docker exec -i ${DB_CONTAINER_NAME} psql -U ${DB_ROOT_USER} -c "CREATE DATABASE ${DB_NAME} OWNER ${DB_USER};" 2>/dev/null || echo "이미 존재하는 데이터베이스입니다."
# 권한 부여
docker exec -i ${DB_CONTAINER_NAME} psql -U ${DB_ROOT_USER} -c "GRANT ALL PRIVILEGES ON DATABASE ${DB_NAME} TO ${DB_USER};"


# 8. 확장 기능(Extension) 등록 (생성된 비즈니스 데이터베이스에 등록)
echo "🧩 PostgreSQL 확장 기능(pg_cron, pgmq, pg_stat_statements) 활성화 중..."
docker exec -i ${DB_CONTAINER_NAME} psql -U ${DB_ROOT_USER} -d ${DB_NAME} -c "CREATE EXTENSION IF NOT EXISTS pg_stat_statements;"
docker exec -i ${DB_CONTAINER_NAME} psql -U ${DB_ROOT_USER} -d ${DB_NAME} -c "CREATE EXTENSION IF NOT EXISTS pg_cron;"
docker exec -i ${DB_CONTAINER_NAME} psql -U ${DB_ROOT_USER} -d ${DB_NAME} -c "CREATE EXTENSION IF NOT EXISTS pgmq;"


echo "========================================="
echo "🎉 설정 완료!"
echo "-----------------------------------------"
echo "▶ 1. PostgreSQL 접속정보"
echo "  - Host: localhost:5432"
echo "  - User: ${DB_USER}"
echo "  - Password: ${DB_PW}"
echo "  - Database: ${DB_NAME}"
echo ""
echo "▶ 2. Redis 접속정보"
echo "  - Host: localhost:${REDIS_PORT}"
echo ""
echo "▶ 3. Redis Commander 접속"
echo "  - URL: http://localhost:${REDIS_COMMANDER_PORT}"
echo ""
echo "▶ 4. pgAdmin 접속"
echo "  - URL: http://localhost:${PGADMIN_PORT}"
echo "  - ID: ${PGADMIN_EMAIL} / PW: ${PGADMIN_PW}"
echo "========================================="
