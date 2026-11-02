# 새 컴퓨터 세팅 매뉴얼

처음 받은 컴퓨터(노트북, 예비 PC 등)에서 이 프로젝트를 돌리는 순서예요. 비밀값은 이 문서에 쓰지 않고, 각자 만들어서 `backend/.env`에만 둬요(커밋 안 됨).

## 먼저 알아둘 것

- **레포로 같이 오는 것:** 코드, 마이그레이션(표 구조), 샘플 공지, `backend/db/docker-compose.yml`(도커 설계도), DB 만들기 스크립트.
- **컴퓨터마다 새로 만드는 것:** 도커 프로그램, DB 데이터, `backend/.env`(비밀번호·JWT 키). DB 데이터는 컴퓨터끼리 공유되지 않아요.
- **어디까지 필요한가요?**

| 하려는 일 | 필요한 것 |
| --- | --- |
| 화면만 만지기 | Git, Node.js 24 (1번) |
| 백엔드 테스트만 돌리기(`./gradlew test`) | 위 + JDK 17 (도커 불필요, H2 메모리 DB) |
| 로그인·공지 등 실제 데이터로 확인하기 | 위 + Docker Desktop (2번) |

## 설치할 프로그램

| 프로그램 | 비고 |
| --- | --- |
| Git | Windows는 **Git Bash**도 같이 설치돼요. 아래 `.sh` 스크립트는 Git Bash(또는 맥 터미널)에서 실행해요 |
| Node.js 24 | 프론트용 |
| JDK 17 | 백엔드용. 내 JDK 버전과 관계없이 Java 17 문법으로만 컴파일돼요 |
| Docker Desktop | 실제 DB를 쓸 때만. Windows는 설치 화면에서 WSL 2를 고르고, 설치 뒤 **앱을 실행해 왼쪽 아래가 초록색(Engine running)**이 될 때까지 기다려요 |

`psql`은 설치하지 않아도 돼요. PC에 `psql`이 없으면 DB 스크립트가 도커 컨테이너 안의 `psql`을 대신 써요.

## 1. 화면만 보기

```bash
git clone https://github.com/SpicyShrimpChip/lms.git
cd lms/frontend
npm ci          # Git 훅도 함께 설치돼요. 패키지가 바뀐 뒤에는 다시 실행해요
npm run dev     # http://localhost:5173
```

기본이 목업 데이터(`VITE_USE_MOCK=true`)라 백엔드 없이 모든 화면이 보여요.

## 2. 백엔드와 DB (실제 데이터)

PostgreSQL(pgvector 포함)은 도커로 켜요. `docker-compose.yml`이 있는 폴더에서 직접 명령을 실행해야 도커가 그 파일을 읽고 이미지를 내려받아요.

**① PostgreSQL 컨테이너 켜기 (처음 한 번)**

```bash
cd lms/backend/db
POSTGRES_PASSWORD='<관리자 비밀번호>' docker compose up -d
```

PowerShell이라면 아래처럼 해요. 이 비밀번호는 이 컴퓨터의 컨테이너 안에서만 쓰여요.

```bash
$env:POSTGRES_PASSWORD="<관리자 비밀번호>"; docker compose up -d
```

> `docker compose` 명령은 `ps`, `exec`처럼 켜기가 아닌 것도 `POSTGRES_PASSWORD` 변수가 없으면 오류를 내요. 컨테이너를 켠 뒤에는 아래 "매일 쓰는 명령"의 `docker start` 같은 일반 도커 명령을 쓰면 편해요.

**② DB와 계정 만들기, `backend/.env` 만들기 (처음 한 번)**

```bash
cd ..
./db/setup-local-postgres.sh
```

- 앱용·마이그레이션용 계정 비밀번호를 물어봐요(12자 이상). 비밀번호는 `backend/.env`에만 저장되고, JWT 키는 자동으로 만들어져요.
- `ai_lms`(서비스)와 `ai_lms_test`(테스트 전용) 두 DB가 만들어지고, 두 DB 모두에 `vector` 확장이 켜져요.
- PC에 `psql`이 있으면 관리자 비밀번호를 묻고 PC의 `psql`로, 없으면 도커 컨테이너 안의 `psql`로 접속해요(이때는 관리자 비밀번호를 묻지 않아요).

**③ 실행과 테스트**

```bash
./gradlew bootRun    # http://localhost:8080 , 처음 실행할 때 Flyway가 표를 만들어요
./gradlew test       # H2 메모리 DB로 테스트 (도커 불필요)
./gradlew pgTest     # 같은 테스트를 실제 PostgreSQL(ai_lms_test)로
```

프론트를 이 백엔드에 연결하려면 `frontend/.env.development.local`(커밋 안 됨)에 `VITE_USE_MOCK=false`를 넣어요. 끝나면 지워요.

## 매일 쓰는 명령

컨테이너 이름은 `db-postgres-1`이에요(`docker ps`로 확인).

```bash
docker start db-postgres-1     # PC를 껐다 켠 뒤. Docker Desktop을 먼저 실행해야 해요
docker stop db-postgres-1
docker ps                      # 켜진 컨테이너 보기
```

DB 안을 보려면 `docker exec -it db-postgres-1 psql -U ai_lms_migrator ai_lms`. 나갈 때는 `\q`(슬래시가 아니라 백슬래시)예요. 확인용 SQL은 `docs/backend-design.md` 8번.

## VS Code로 DB 보기 (선택)

표를 눈으로 보고 싶으면 VS Code 확장 **PostgreSQL**(Microsoft, `ms-ossdata.vscode-pgsql`, 추천 목록에 들어 있어요)을 설치해요. 왼쪽 코끼리 아이콘 → **Add Connection**에서 아래처럼 채우고 **Test Connection**으로 확인한 뒤 **Save & Connect**를 눌러요. Docker Desktop과 컨테이너가 켜져 있어야 해요.

| 칸 | 값 |
| --- | --- |
| SERVER NAME | `127.0.0.1` (포트는 기본 5432라 따로 안 적어도 돼요) |
| USER NAME | `ai_lms_app` |
| PASSWORD | `backend/.env`의 `DB_PASSWORD` |
| DATABASE NAME | `ai_lms` |

- **계정과 비밀번호는 짝이에요.** `ai_lms_app`은 `DB_PASSWORD`, `ai_lms_migrator`는 `DB_MIGRATION_PASSWORD`예요. 보기만 할 거면 표를 바꿀 수 없는 `ai_lms_app`이 안전해요.
- 비밀번호는 각자 컴퓨터에만 저장되고 레포에는 안 올라가요.
- 확장에는 Copilot AI 기능도 있어요. 실제 사용자 데이터가 든 DB에는 AI 기능을 쓰지 않아요.
- 표 구조를 바꾸는 SQL은 여기서 직접 치지 말고 마이그레이션 파일(`V7__...sql`처럼 다음 번호, `backend/CLAUDE.md` 참고)로 만들어요. 직접 친 SQL은 그 컴퓨터의 DB에만 반영돼요.

## 처음부터 다시 하고 싶을 때

데이터가 모두 지워져요. 샘플 공지는 서버를 켜면 다시 들어와요.

```bash
docker rm -f db-postgres-1
docker volume rm db_pgdata
mv backend/.env ~/lms-env.bak     # 스크립트는 .env가 이미 있으면 덮어쓰지 않고 멈춰요
```

그다음 2번의 ①부터 다시 해요.

## 막힐 때

| 증상 | 원인과 해결 |
| --- | --- |
| `docker` 명령을 찾을 수 없어요 | 설치하기 **전에** 열어 둔 터미널·앱은 새 PATH를 몰라요. 터미널(그래도 안 되면 앱)을 껐다 켜요 |
| `//./pipe/docker_engine` 연결 오류 | Docker Desktop이 안 켜진 거예요. 앱을 실행해 초록색이 될 때까지 기다려요. 계속 멈추면 `wsl --update` 후 재부팅 |
| `required variable POSTGRES_PASSWORD is missing` | `docker compose` 명령은 매번 이 변수가 필요해요. 위 ①처럼 값을 넣거나, 켜진 컨테이너는 `docker start` 같은 일반 도커 명령을 써요 |
| `port is already allocated` (5432) | 이 PC에 다른 PostgreSQL이 5432를 쓰고 있어요. 그걸 끄거나 `docker-compose.yml`의 포트를 `127.0.0.1:5433:5432`로 바꾸고 `DB_PORT=5433`으로 스크립트를 실행해요 |
| 스크립트가 "backend/.env가 이미 있어요" | 덮어쓰지 않으려고 멈춘 거예요. 파일을 레포 밖으로 옮긴 뒤 다시 실행해요 |
| 스크립트가 "이미 있는 항목"이라며 멈춰요 | 이전에 만든 DB·계정이 남아 있어요. 위 "처음부터 다시"로 지우거나, 그대로 쓸 거면 `backend/.env`를 직접 채워요 |
| `extension "vector" is not available` | 컨테이너가 pgvector가 없는 일반 `postgres` 이미지예요. `docker-compose.yml`의 이미지(`pgvector/pgvector`)로 다시 만들어요 |
| 백엔드가 `password authentication failed` | `backend/.env`의 계정 비밀번호가 DB와 달라요. DB를 새로 만들고 스크립트를 다시 실행해요 |
| VS Code 확장에서 `getaddrinfo failed` | SERVER NAME에 `localhost:5432`처럼 포트를 붙였어요. 이 칸은 호스트 이름만 받아요. `127.0.0.1`만 적어요 |
| VS Code 확장에서 15초 뒤 시간 초과 | `localhost`가 IPv6로 먼저 시도돼서 그래요(컨테이너는 `127.0.0.1`에만 열려 있어요). SERVER NAME을 `127.0.0.1`로 바꿔요 |
| VS Code 확장에서 `password authentication failed` | 계정과 비밀번호 짝이 안 맞아요. `ai_lms_app`↔`DB_PASSWORD`, `ai_lms_migrator`↔`DB_MIGRATION_PASSWORD`로 확인하고, 복사할 때 `+ / =` 앞에서 끊기거나 공백이 붙지 않았는지 봐요 |
| 테스트가 `_test로 끝나는 DB` 오류로 멈춤 | 일부러 막은 거예요. 테스트는 회원 행을 지우니 `ai_lms_test`에서만 돌아요. `TEST_DB_URL` 환경변수를 설정했다면 이름이 `_test`로 끝나는 DB를 가리키는지 확인해요 |
| 마이그레이션 오류(`checksum`, 이미 적용됨) | 옛 DB에 남은 기록과 파일이 달라요. DB를 새로 만들어요 |
| Windows에서 `.sh`가 안 돌아요 | PowerShell이 아니라 **Git Bash**에서 실행해요 |
| PowerShell에서 `./gradlew`가 안 돌아요 | PowerShell에서는 `.\gradlew.bat bootRun`처럼 써요. Git Bash에서는 `./gradlew` 그대로 돼요 |

## 그다음 읽을 것

- 프로젝트 규칙과 미결 사항: `CLAUDE.md`, `backend/CLAUDE.md`, `frontend/CLAUDE.md`
- 겪은 에러와 해결: `docs/wiki/` (작업 전에 `README.md` 목차를 훑어요)
- 백엔드 설계: `docs/backend-design.md`
- 프론트가 백엔드에 바라는 API: `docs/frontend-requests.md`
