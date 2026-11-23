#!/bin/bash
# 운영 DB와 계정 두 개를 만든다. PostgreSQL 컨테이너가 데이터 폴더가 비어 있을 때(처음 띄울 때) 한 번만 실행한다.
# backend/db/setup-local-postgres.sh·CI(backend-ci.yml)와 같은 구성이다:
#   msg_migrator: 테이블 생성·변경(Flyway 마이그레이션 전용, DB 소유자)
#   msg_app     : 데이터 읽기·쓰기만 (백엔드가 쓰는 계정)
# vector 확장은 슈퍼유저만 만들 수 있어 여기서 미리 만든다(Flyway의 db/vector는 IF NOT EXISTS라 그대로 통과한다).
# 비밀번호는 compose가 넘긴 환경변수로만 받고, psql 변수로 넘겨 SQL에 직접 이어 붙이지 않는다.
set -euo pipefail

: "${APP_DB_PASSWORD:?APP_DB_PASSWORD가 비어 있어요}"
: "${MIGRATION_DB_PASSWORD:?MIGRATION_DB_PASSWORD가 비어 있어요}"

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
	-v app_password="$APP_DB_PASSWORD" \
	-v migration_password="$MIGRATION_DB_PASSWORD" <<'SQL'
CREATE ROLE msg_migrator LOGIN PASSWORD :'migration_password';
CREATE ROLE msg_app LOGIN PASSWORD :'app_password';
CREATE DATABASE msg OWNER msg_migrator ENCODING 'UTF8' TEMPLATE template0;
REVOKE ALL ON DATABASE msg FROM PUBLIC;
GRANT CONNECT ON DATABASE msg TO msg_app;
\connect msg
CREATE EXTENSION IF NOT EXISTS vector;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO msg_app;
GRANT ALL ON SCHEMA public TO msg_migrator;
ALTER DEFAULT PRIVILEGES FOR ROLE msg_migrator IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO msg_app;
ALTER DEFAULT PRIVILEGES FOR ROLE msg_migrator IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO msg_app;
SQL
