#!/usr/bin/env bash
# PostgreSQL(pgvector)에 MSG 전용 DB와 계정을 만들고 backend/.env를 쓴다. 직접 터미널에서 실행한다:
#   cd backend && ./db/setup-local-postgres.sh
#
# 하는 일
#   1. 관리자(postgres) 비밀번호를 입력받아 기존 DB·계정 목록을 보여 준다 (읽기만 함)
#   2. ai_lms / ai_lms_test DB나 ai_lms_app / ai_lms_migrator 계정이 이미 있으면 아무것도 바꾸지 않고 멈춘다
#   3. DB 2개와 계정 2개를 만들고 각 DB에만 최소 권한을 준다
#        ai_lms_migrator: 테이블 생성·변경(Flyway 마이그레이션 전용, DB 소유자)
#        ai_lms_app     : 데이터 읽기·쓰기만 (애플리케이션이 쓰는 계정)
#   4. backend/.env(커밋 안 됨, 권한 600)에 접속 정보와 JWT 시크릿을 저장한다
#
# 이 PC에 psql이 없으면 도커 컨테이너(backend/db/docker-compose.yml)의 psql로 대신한다. 이때는 컨테이너 안에서 접속하므로
# 관리자 비밀번호를 묻지 않는다. psql이 있으면(원격 DB 포함) 지금처럼 PC의 psql로 접속한다.
#
# vector 확장은 슈퍼유저만 만들 수 있어서 이 스크립트가 두 DB에 미리 만든다 (Flyway의 db/vector 마이그레이션은 IF NOT EXISTS라 그대로 통과한다).
# 비밀번호는 화면에 보이지 않게 입력받고, 명령줄 인자·셸 기록에 남기지 않는다.
set -euo pipefail

PSQL="${PSQL:-psql}"
ADMIN_USER="${PG_ADMIN_USER:-postgres}"
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DATABASES=(ai_lms ai_lms_test)
APP_USER="ai_lms_app"
MIGRATION_USER="ai_lms_migrator"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ENV_FILE="$SCRIPT_DIR/../.env"

# psql이 없으면 켜져 있는 docker-compose.yml의 postgres 컨테이너 안에서 실행한다 (컨테이너 이름이 바뀌어도 compose가 찾아 준다)
MODE=host
CONTAINER=""
if ! command -v "$PSQL" >/dev/null 2>&1; then
	if command -v docker >/dev/null 2>&1; then
		# compose 파일이 POSTGRES_PASSWORD를 요구하므로 ps에만 쓰는 값을 넣는다 (컨테이너의 실제 비밀번호와 무관)
		CONTAINER="$(POSTGRES_PASSWORD=unused docker compose -f "$SCRIPT_DIR/docker-compose.yml" ps -q postgres 2>/dev/null || true)"
	fi
	if [ -z "$CONTAINER" ]; then
		echo "psql 명령을 찾을 수 없고 켜진 도커 컨테이너도 없어요. 'cd backend/db && docker compose up -d'로 켜거나, PSQL=/경로/psql 로 지정하거나 'brew install libpq'로 설치해 주세요." >&2
		exit 1
	fi
	MODE=docker
fi

if [ -e "$ENV_FILE" ]; then
	echo "backend/.env가 이미 있어요. 덮어쓰지 않도록 멈춥니다. 필요하면 파일을 옮긴 뒤 다시 실행해 주세요." >&2
	exit 1
fi

# 관리자 비밀번호는 권한 600 임시 .pgpass 파일로만 psql에 넘기고, 끝나면 지운다
PGPASS_FILE="$(mktemp)"
chmod 600 "$PGPASS_FILE"
trap 'rm -f "$PGPASS_FILE"' EXIT
export PGPASSFILE="$PGPASS_FILE"

# .pgpass 형식: ':' 와 '\' 를 '\' 로 이스케이프
pgpass_escape() { printf '%s' "$1" | sed -e 's/\\/\\\\/g' -e 's/:/\\:/g'; }
# SQL 문자열: 작은따옴표를 두 번 쓴다
sql_escape() { printf '%s' "$1" | sed -e "s/'/''/g"; }
# Spring이 읽는 .properties: \
prop_escape() { printf '%s' "$1" | sed -e 's/\\/\\\\/g'; }

if [ "$MODE" = host ]; then
	read -r -s -p "PostgreSQL 관리자($ADMIN_USER) 비밀번호: " ADMIN_PASSWORD
	echo
	printf '%s:%s:*:%s:%s\n' "$DB_HOST" "$DB_PORT" "$ADMIN_USER" "$(pgpass_escape "$ADMIN_PASSWORD")" >"$PGPASS_FILE"
	unset ADMIN_PASSWORD
else
	echo "psql이 없어 도커 컨테이너 안의 psql로 접속해요 (관리자 비밀번호는 묻지 않아요)."
fi

# 관리자로 실행하는 psql. 첫 인자는 접속할 DB, 나머지는 psql 인자 (SQL은 표준 입력으로도 넘길 수 있다)
psql_admin() {
	local db=$1
	shift
	if [ "$MODE" = docker ]; then
		# -c로 SQL을 넘길 때는 표준 입력을 연결하지 않는다 (연결하면 뒤에 읽을 입력까지 docker exec가 가져간다)
		local stdin_flag="-i" arg
		for arg in "$@"; do
			[ "$arg" = "-c" ] && stdin_flag=""
		done
		# shellcheck disable=SC2086
		docker exec $stdin_flag "$CONTAINER" psql -U "$ADMIN_USER" -d "$db" -v ON_ERROR_STOP=1 -X -At "$@"
	else
		"$PSQL" -h "$DB_HOST" -p "$DB_PORT" -U "$ADMIN_USER" -d "$db" -v ON_ERROR_STOP=1 -X -At "$@"
	fi
}

run_sql() {
	psql_admin postgres "$@"
}

echo
echo "== PostgreSQL 서버"
run_sql -c "SELECT version();"

echo
echo "== 기존 데이터베이스 (변경하지 않음)"
run_sql -c "SELECT datname FROM pg_database WHERE NOT datistemplate ORDER BY 1;"

echo
echo "== 기존 계정 (변경하지 않음)"
run_sql -c "SELECT rolname FROM pg_roles WHERE rolname NOT LIKE 'pg\_%' ORDER BY 1;"

existing_db=$(run_sql -c "SELECT datname FROM pg_database WHERE datname IN ('ai_lms', 'ai_lms_test');")
existing_user=$(run_sql -c "SELECT rolname FROM pg_roles WHERE rolname IN ('$APP_USER', '$MIGRATION_USER');")
if [ -n "$existing_db" ] || [ -n "$existing_user" ]; then
	echo
	echo "이미 있는 항목이 있어 아무것도 바꾸지 않고 멈춥니다:" >&2
	[ -n "$existing_db" ] && echo "  DB: $existing_db" >&2
	[ -n "$existing_user" ] && echo "  계정: $existing_user" >&2
	exit 1
fi

read_password() {
	local label=$1 first second
	while true; do
		read -r -s -p "$label 비밀번호 (12자 이상): " first
		echo >&2
		read -r -s -p "$label 비밀번호 다시 입력: " second
		echo >&2
		if [ "$first" != "$second" ]; then
			echo "두 번 입력한 비밀번호가 달라요. 다시 입력해 주세요." >&2
		elif [ "${#first}" -lt 12 ]; then
			echo "12자 이상으로 정해 주세요." >&2
		else
			printf '%s' "$first"
			return
		fi
	done
}

echo
echo "새로 만들 계정의 비밀번호를 정해 주세요. (backend/.env에만 저장되고 커밋되지 않아요)"
APP_PASSWORD=$(read_password "$APP_USER")
MIGRATION_PASSWORD=$(read_password "$MIGRATION_USER")

# 계정·DB 생성 (비밀번호가 들어가므로 표준 입력으로만 넘긴다)
{
	echo "CREATE ROLE $MIGRATION_USER LOGIN PASSWORD '$(sql_escape "$MIGRATION_PASSWORD")';"
	echo "CREATE ROLE $APP_USER LOGIN PASSWORD '$(sql_escape "$APP_PASSWORD")';"
	for db in "${DATABASES[@]}"; do
		echo "CREATE DATABASE $db OWNER $MIGRATION_USER ENCODING 'UTF8' TEMPLATE template0;"
		# 다른 계정이 접속하지 못하게 기본 공개 권한을 거두고, 앱 계정에만 접속을 허용한다
		echo "REVOKE ALL ON DATABASE $db FROM PUBLIC;"
		echo "GRANT CONNECT ON DATABASE $db TO $APP_USER;"
	done
} | run_sql

# DB별 설정: vector 확장(슈퍼유저 필요), 앱 계정의 테이블 권한 (migrator가 만드는 테이블에 자동 적용)
for db in "${DATABASES[@]}"; do
	psql_admin "$db" -q <<SQL
CREATE EXTENSION IF NOT EXISTS vector;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO $APP_USER;
GRANT ALL ON SCHEMA public TO $MIGRATION_USER;
ALTER DEFAULT PRIVILEGES FOR ROLE $MIGRATION_USER IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO $APP_USER;
ALTER DEFAULT PRIVILEGES FOR ROLE $MIGRATION_USER IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO $APP_USER;
SQL
done

JWT_SECRET=$(openssl rand -base64 48)
umask 077
cat >"$ENV_FILE" <<ENV
# 로컬 전용 비밀값 (setup-local-postgres.sh가 만듦). 절대 커밋하지 않는다
DB_URL=jdbc:postgresql://$DB_HOST:$DB_PORT/ai_lms
DB_USERNAME=$APP_USER
DB_PASSWORD=$(prop_escape "$APP_PASSWORD")
DB_MIGRATION_USERNAME=$MIGRATION_USER
DB_MIGRATION_PASSWORD=$(prop_escape "$MIGRATION_PASSWORD")
JWT_SECRET=$JWT_SECRET
ENV
unset APP_PASSWORD MIGRATION_PASSWORD JWT_SECRET

echo
echo "== 만든 항목"
run_sql -c "SELECT datname, pg_get_userbyid(datdba) FROM pg_database WHERE datname IN ('ai_lms', 'ai_lms_test') ORDER BY 1;"
echo
echo "완료했어요. backend/.env를 만들었습니다 (권한 600)."
echo "테이블은 서버를 처음 띄울 때 Flyway가 만듭니다: ./gradlew bootRun"
