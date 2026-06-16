-- 1. 통합 일반 유저 생성
DO $$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'mjusugangsincheonghelperuser') THEN
    CREATE ROLE mjusugangsincheonghelperuser WITH LOGIN PASSWORD 'mjusugangsincheonghelperpassword' SUPERUSER;
  END IF;
END
$$;

-- 2. Dev 및 Test 데이터베이스 생성
-- (PostgreSQL 초기화 스크립트는 기본 DB에서 실행되므로 gexec 대신 단순 SQL 사용)
CREATE DATABASE mjusugangsincheonghelperdb_dev OWNER mjusugangsincheonghelperuser;
CREATE DATABASE mjusugangsincheonghelperdb_test OWNER mjusugangsincheonghelperuser;

-- 3. 각 데이터베이스에 접속하여 확장 기능 설치
-- Dev DB 설정
\c mjusugangsincheonghelperdb_dev
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;
CREATE EXTENSION IF NOT EXISTS pg_cron;
CREATE EXTENSION IF NOT EXISTS pgmq;
CREATE EXTENSION IF NOT EXISTS system_stats;
ALTER DATABASE mjusugangsincheonghelperdb_dev SET search_path TO public;

-- Test DB 설정
\c mjusugangsincheonghelperdb_test
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;
CREATE EXTENSION IF NOT EXISTS pgmq;
ALTER DATABASE mjusugangsincheonghelperdb_test SET search_path TO public;

-- 공통 유저 설정
ALTER ROLE mjusugangsincheonghelperuser SET search_path TO public, 'mjusugangsincheonghelperuser';
