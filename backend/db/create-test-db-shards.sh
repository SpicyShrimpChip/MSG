#!/usr/bin/env bash
# pgTest를 JVM 여러 개로 나눠 돌릴 때 쓰는 테스트 DB(msg_1_test ~ msg_N_test)를 만든다. 이미 있는 DB는 건너뛴다.
# setup-local-postgres.sh로 msg_test와 두 계정(msg_app·msg_migrator)을 만든 뒤 한 번 실행한다:
#   cd backend && ./db/create-test-db-shards.sh
# 그다음 ~/.gradle/gradle.properties(내 PC에만, 커밋 안 됨)에 msgTestDbShards=4 를 넣으면 pgTest가 4개로 나눠 돈다.
# 넣지 않으면 pgTest는 예전처럼 msg_test 하나로 돈다. CI는 이 스크립트를 PGPASSWORD와 함께 부른다(backend-ci.yml).
#
# 권한은 msg_test와 같다: migrator가 DB 소유자(테이블 생성·변경), 앱 계정은 데이터 읽기·쓰기만, vector 확장은 관리자가 미리 설치.
# 이 PC에 psql이 없으면 도커 컨테이너(backend/db/docker-compose.yml)의 psql로 대신한다(관리자 비밀번호를 묻지 않음).
set -euo pipefail

PSQL="${PSQL:-psql}"
ADMIN_USER="${PG_ADMIN_USER:-postgres}"
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
SHARDS="${TEST_DB_SHARDS:-4}"
APP_USER="msg_app"
MIGRATION_USER="msg_migrator"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

case "$SHARDS" in
	'' | *[!0-9]*) echo "TEST_DB_SHARDS는 숫자여야 해요: $SHARDS" >&2; exit 1 ;;
esac

MODE=host
CONTAINER=""
if ! command -v "$PSQL" >/dev/null 2>&1; then
	if command -v docker >/dev/null 2>&1; then
		CONTAINER="$(POSTGRES_PASSWORD=unused docker compose -f "$SCRIPT_DIR/docker-compose.yml" ps -q postgres 2>/dev/null || true)"
	fi
	if [ -z "$CONTAINER" ]; then
		echo "psql 명령을 찾을 수 없고 켜진 도커 컨테이너도 없어요. 'cd backend/db && docker compose up -d'로 켜 주세요." >&2
		exit 1
	fi
	MODE=docker
elif [ -z "${PGPASSWORD:-}" ]; then
	read -r -s -p "PostgreSQL 관리자($ADMIN_USER) 비밀번호: " PGPASSWORD
	echo
	export PGPASSWORD
fi

# 관리자로 실행하는 psql. 첫 인자는 접속할 DB, SQL은 표준 입력으로 넘긴다
psql_admin() {
	local db=$1
	shift
	if [ "$MODE" = docker ]; then
		docker exec -i "$CONTAINER" psql -U "$ADMIN_USER" -d "$db" -v ON_ERROR_STOP=1 -X -At "$@"
	else
		"$PSQL" -h "$DB_HOST" -p "$DB_PORT" -U "$ADMIN_USER" -d "$db" -v ON_ERROR_STOP=1 -X -At "$@"
	fi
}

roles=$(echo "SELECT count(*) FROM pg_roles WHERE rolname IN ('$APP_USER', '$MIGRATION_USER');" | psql_admin postgres)
if [ "$roles" != "2" ]; then
	echo "계정 $APP_USER·$MIGRATION_USER가 없어요. 먼저 ./db/setup-local-postgres.sh로 만들어 주세요." >&2
	exit 1
fi

for i in $(seq 1 "$SHARDS"); do
	db="msg_${i}_test"
	exists=$(echo "SELECT 1 FROM pg_database WHERE datname = '$db';" | psql_admin postgres)
	if [ "$exists" = "1" ]; then
		echo "$db: 이미 있어요"
		continue
	fi
	psql_admin postgres -q <<SQL
CREATE DATABASE $db OWNER $MIGRATION_USER ENCODING 'UTF8' TEMPLATE template0;
REVOKE ALL ON DATABASE $db FROM PUBLIC;
GRANT CONNECT ON DATABASE $db TO $APP_USER;
SQL
	psql_admin "$db" -q <<SQL
CREATE EXTENSION IF NOT EXISTS vector;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO $APP_USER;
GRANT ALL ON SCHEMA public TO $MIGRATION_USER;
ALTER DEFAULT PRIVILEGES FOR ROLE $MIGRATION_USER IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO $APP_USER;
ALTER DEFAULT PRIVILEGES FOR ROLE $MIGRATION_USER IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO $APP_USER;
SQL
	echo "$db: 만들었어요"
done
