# CI·Git·배포 교훈

## 패키지가 늘어난 뒤에는 다른 컴퓨터에서 `npm ci`를 다시 한다 (2026-10-28, #72·#74)

- 상황: `clsx`·`tailwind-merge`·`prettier-plugin-tailwindcss`가 추가된 뒤 main을 받았다.
- 원인: 패키지가 설치되지 않은 상태에서 커밋하면 커밋 훅(lint-staged)이 정렬 플러그인을 찾지 못해 오류로 멈춘다.
- 해결: main을 받은 뒤 `frontend`에서 `npm ci`를 한 번 한다.
- 다음에는: `package.json`이 바뀐 PR을 받았다면 먼저 `npm ci`를 한다.

## Prettier 검사는 CI가, 고치는 건 커밋 훅이 한다 (2026-10-28, #75)

- 상황: 커밋 훅을 건너뛰거나 GitHub 웹에서 파일을 고친 경우 형식이 어긋난다.
- 원인: CI가 형식을 자동으로 고치게 하면, CI가 만든 커밋은 CI를 다시 돌리지 않고 쓰기 권한도 필요하다.
- 해결: `frontend-ci.yml`이 `npm run format:check`로 검사만 한다. 실패하면 `frontend`에서 `npm run format` 후 커밋한다.
- 다음에는: `package-lock.json`은 npm이 만드는 파일이라 `.prettierignore`로 제외한다.

## VS Code PostgreSQL 확장 접속 오류 세 가지 (2026-10-31, #84)

- 상황: 도커 PostgreSQL에 VS Code 확장(`ms-ossdata.vscode-pgsql`)으로 처음 접속했다.
- 원인: ① SERVER NAME에 `localhost:5432`처럼 포트를 붙이면 통째로 호스트 이름으로 읽어 `getaddrinfo failed`가 난다. ② `localhost`가 IPv6로 먼저 시도돼 15초 뒤 시간 초과가 났다(컨테이너는 `127.0.0.1`에만 열려 있다). ③ 앱 계정과 마이그레이션 계정의 비밀번호를 서로 바꿔 넣으면 `password authentication failed`가 난다.
- 해결: SERVER NAME을 `127.0.0.1`로 하고 포트는 비워 둔다. 계정은 `msg_app`↔`DB_PASSWORD`, `msg_migrator`↔`DB_MIGRATION_PASSWORD`로 짝을 맞춘다.
- 다음에는: 보기만 할 때는 표를 못 바꾸는 앱 계정을 쓴다. 자세한 값은 `docs/setup.md` "VS Code로 DB 보기".

## 도커: `docker --version`은 되는데 연결 오류가 난다 (2026-10-31)

- 상황: Windows에서 Docker Desktop을 막 설치하고 `docker run`을 했더니 `//./pipe/docker_engine` 연결 오류가 났다. 이미 열어 둔 터미널·앱에서는 `docker` 명령 자체를 못 찾기도 했다.
- 원인: `docker` 명령은 설치만 되면 나오지만 엔진은 Docker Desktop 앱을 실행해야 켜진다. 설치하기 전에 열어 둔 프로그램은 새 PATH를 모른다.
- 해결: Docker Desktop을 실행해 왼쪽 아래가 초록색(Engine running)이 된 뒤 다시 실행한다. 명령을 못 찾으면 터미널(안 되면 앱)을 껐다 켠다. 계속 멈추면 `wsl --update` 후 재부팅.
- 다음에는: `docker compose` 명령은 `docker-compose.yml`의 필수 환경변수(`POSTGRES_PASSWORD`)가 없으면 `ps`·`exec`에도 오류를 낸다. 켜 둔 컨테이너는 `docker start`·`docker exec` 같은 일반 도커 명령을 쓴다. 세팅 순서는 `docs/setup.md`.

## 백엔드 CI를 필수 검사로 켜는 순서 (2026-10-27, #65)

- 상황: 규칙셋 "backend CI required"를 켜려 한다(지금은 꺼 둠).
- 원인: 백엔드 CI가 `paths` 필터로 `backend/` 변경 때만 돈다. 필수로 켜면 `backend/` 밖만 바꾼 PR은 검사가 돌지 않아 머지되지 않는다.
- 해결: ① `backend-ci.yml`의 `pull_request`에서 `paths` 줄을 지운다. ② 규칙셋을 Active로 바꾼다. 순서가 반대면 안 된다.
- 다음에는: 필수 검사 이름은 job 이름(`build-and-test`, `postgres-test`)과 같아야 한다. job 이름을 바꾸면 규칙셋도 같이 고친다.

## npm 12가 공식 registry 파일까지 막는다 (2026-10-28, #73)

- 상황: Renovate의 lockfile 갱신이 실패한다.
- 원인: npm 12.0.0~12.2.1에서 공식 registry tarball까지 막는 문제(`EALLOWREMOTE`)가 재현됐다.
- 해결: `.github/renovate.json5`에 `constraints: { npm: "<12" }`를 임시로 넣었다.
- 다음에는: npm에서 고쳐지면 이 줄을 지운다. 확인은 최신 npm으로 `@tailwindcss/vite`를 올려 lockfile이 갱신되는지 본다.

## Claude는 강제 푸시가 막혀 있다 (2026-10-28, #74)

- 상황: 머지된 PR 위에 쌓은 브랜치를 main 위로 옮겨야 했다.
- 원인: Claude에게는 강제 푸시(`--force`) 권한이 없다.
- 해결: 옮긴 브랜치를 새 이름으로 올리고, 옛 원격 브랜치는 지운다.

## GitHub Actions 대기열이 길 때 (2026-10-27)

- 상황: PR을 올렸는데 CI가 몇 분 동안 시작하지 않았다(한 번은 7분).
- 원인: Actions 대기열이 밀린 것이다.
- 해결: 오래 걸리면 최신 main을 합쳐 다시 올린다. 새 실행으로 돈다.

## Vercel 환경변수가 빠지면 화면이 `/api`를 실제로 부른다 (2026-10-27, #43)

- 상황: 백엔드가 없는 목업 모드로 배포했다.
- 원인: `.env.development`는 개발 서버에서만 읽히고, 배포 빌드에는 쓰이지 않는다. `VITE_USE_MOCK`이 빠지면 화면이 `/api`를 실제로 호출해 오류가 난다.
- 해결: Vercel 환경변수에 `VITE_USE_MOCK`(Production·Preview 모두)과 `HUSKY=0`(배포 서버에는 `.git`이 없다)을 넣는다.
- 다음에는: `VITE_`로 시작하는 값은 빌드 결과에 그대로 들어가 누구나 볼 수 있으므로 비밀값을 넣지 않는다. 자세한 설정은 `docs/deploy.md`.

## 빌드 폴더에 `파일 2.sql` 같은 복사본이 생기면 테스트가 깨진다 (2026-11-01, #94)

- 상황: Desktop 아래 폴더에서 `./gradlew test`를 했더니 `Found more than one migration with version 2`, `AuthApiTest 2 (wrong name)`로 실패했다.
- 원인: `backend/build`(그리고 `frontend/dist`)에 이름 끝에 ` 2`가 붙은 복사본이 생겼다. 소스 폴더에는 없었다. 폴더 동기화(iCloud 등)가 만든 것으로 보이며 원인은 확인하지 못했다.
- 해결: `./gradlew clean test`로 빌드 폴더를 지우고 다시 만든다. 소스·`git status`에는 영향이 없다.
- 다음에는: 같은 오류가 나면 `find backend/build -name "* 2*"`로 확인한다. 자주 생기면 레포를 동기화하지 않는 폴더로 옮긴다.
- 재발(2026-11-03): `build/classes`에 `BackendApplication 2.class`가 생겨 `bootRun`이 `Unable to find a single main class`로 멈췄다. 같은 날 `build/resources`의 마이그레이션 사본도 다시 생겼다. 해결은 같고(`clean`), `src/`에는 생기지 않았다.
- 재발(2026-11-24): `clean test`로 고쳐도 다음 실행 사이에 사본(`V1__init_schema 2.sql`, `application-local 2.yaml`)이 다시 생겨 264개가 실패했다. 레포를 옮기지 않고 Gradle 초기화 스크립트로 빌드 폴더만 동기화하지 않는 곳에 두어 피했다(`allprojects { layout.buildDirectory = file('<동기화 안 되는 폴더>') }`를 담은 파일을 `./gradlew -I <파일> test`). 레포 파일은 바꾸지 않는다.

## `bootRun`이 MySQL 드라이버를 찾으며 뜨지 않는다 (2026-11-01, #96)

- 상황: 기본 프로필(H2)로 `./gradlew bootRun`을 했는데 `Failed to load driver class com.mysql.cj.jdbc.Driver`로 멈췄다.
- 원인: `application.yaml`은 `backend/.env`(커밋 안 됨)를 읽어 DB 접속 값을 덮는다. MySQL 시절의 접속 값이 로컬 `.env`(또는 셸 환경변수)에 남아 있던 것으로 보인다(`.env`는 열어 보지 않았다). `./gradlew test`는 영향을 받지 않았다.
- 해결: `.env`를 `backend/.env.example`(PostgreSQL)에 맞춰 다시 만든다. 잠깐 H2로만 띄울 때는 `SPRING_DATASOURCE_URL`·`SPRING_DATASOURCE_USERNAME`·`SPRING_DATASOURCE_PASSWORD`·`SPRING_DATASOURCE_DRIVER_CLASS_NAME` 환경변수로 덮는다(환경변수가 `.env`보다 우선).
- 다음에는: DB를 바꾸는 PR 뒤에는 각자 `.env`도 `.env.example`과 비교한다.

## Windows Git Bash에서 curl로 한글 JSON을 보내면 400이 난다 (2026-11-16, #165)

- 상황: 백엔드 Docker 이미지를 띄우고 curl로 회원가입을 확인했더니 "요청 형식이 올바르지 않아요"(400)가 났다.
- 원인: `-d '{"name":"테스트"}'`처럼 명령줄에 직접 쓴 한글이 UTF-8이 아닌 코드 페이지로 넘어갔다. 응답 JSON을 파이썬 `open()`으로 읽을 때도 기본 인코딩(cp949) 때문에 깨졌다.
- 해결: 요청 본문을 UTF-8 파일로 저장하고 `--data-binary @파일`로 보낸다. 응답은 `open(..., encoding='utf-8')`로 읽는다.
- 다음에는: 로그인·갱신 API는 `X-Requested-With: XMLHttpRequest` 헤더가 없으면 403이니 같이 넣는다(`docs/deploy.md`).

## 개발 서버를 다른 포트로 띄우면 실제 API 요청이 전부 403이다 (2026-11-20)

- 상황: 회원가입 자동 가입 방지를 확인하려고 실제 API 모드 개발 서버를 5175 포트로 하나 더 띄웠더니, 가입 요청이 서버 로그 없이 403("요청을 처리하지 못했어요")이 났다.
- 원인: Vite 프록시가 브라우저의 `Origin`(localhost:5175)을 그대로 넘기고, 백엔드 CORS 허용 출처는 5173·5174뿐이라 Spring이 거절했다. CORS 거절은 오류 로그를 남기지 않는다.
- 해결: 확인용 서버도 5174로 띄웠다.
- 다음에는: 실제 API 모드는 5173·5174에서만 띄운다. 다른 포트가 꼭 필요하면 `application.yaml`의 `cors.allowed-origins`에 잠깐 더하고 커밋하지 않는다(운영은 `CORS_ALLOWED_ORIGINS`).

## 쌓은 PR을 squash 머지하면 다음 PR이 충돌하고, base가 main이 아니면 백엔드 CI가 돌지 않는다 (2026-11-21, #175·#176·#177)

- 상황: #175 → #176 → #177을 앞 브랜치 위에 쌓아 올렸다. #176·#177에는 백엔드 CI가 없었고, #175를 squash 머지한 뒤 #176의 base를 main으로 바꾸자 "충돌"이 났다.
- 원인: `backend-ci.yml`은 `branches: ["main"]`인 PR에서만 돈다. squash 머지는 앞 PR 커밋을 새 커밋 하나로 main에 넣어, 다음 브랜치의 원래 커밋과 기록이 달라진다(내용은 같다).
- 해결: `git diff origin/main <앞 브랜치> -- backend`가 비어 있는지(내용이 같은지) 확인하고, `origin/main`을 다음 브랜치에 병합하며 충돌 파일은 다음 브랜치 쪽으로 정했다. 강제 푸시 없이 일반 푸시로 끝났다(위 "Claude는 강제 푸시가 막혀 있다").
- 다음에는: 앞 PR을 머지한 뒤 다음 PR의 base를 main으로 바꾸고, 그 CI가 통과한 걸 보고 머지한다. 쌓은 PR은 로컬 `./gradlew test`로만 확인된 상태임을 PR에 적는다. 프론트 CI(`frontend-ci.yml`)도 base가 main인 PR에서만 돈다(11/21 #186도 같은 방법으로 해결).

## Git Bash에서 `docker compose exec ... /data/...`의 경로가 Windows 경로로 바뀐다 (2026-11-23, #198)

- 상황: 윈도우 PC에서 `deploy/backup.sh`를 로컬 compose로 시험했더니 `tar: C:/Program Files/Git/data/uploads: Cannot open`이 났다.
- 원인: Git Bash(MSYS)가 `/`로 시작하는 인자를 Windows 경로로 바꿔 컨테이너 안 경로가 깨진다. 리눅스 서버에서는 생기지 않는다.
- 해결: 그 명령 앞에 `MSYS_NO_PATHCONV=1`을 붙여 실행했다(스크립트는 고치지 않음). `docker run -v`에 PC 경로를 넘길 때는 `$(pwd -W)`를 썼다.
- 다음에는: 윈도우에서 컨테이너 안 경로를 인자로 넘기는 도커 명령이 이상하면 먼저 `MSYS_NO_PATHCONV=1`로 다시 해 본다.

## Claude의 명령에 읽기 금지 파일이 섞이면 같은 명령의 다른 수정도 실행되지 않는다 (2026-11-24, 보안 강화 PR)

- 상황: 문서를 고치는 스크립트 끝에 `backend/.env.example` 확인(grep)을 붙여 한 번에 실행했더니, 거절 메시지만 나오고 앞의 수정도 하나도 반영되지 않았다.
- 원인: `.claude/settings.json`의 `deny`(`.env*` 읽기 금지)는 명령 전체를 막는다. 앞부분만 실행되는 것이 아니다.
- 해결: 금지 파일을 건드리는 부분을 빼고 다시 실행한 뒤 `git status`로 수정이 반영됐는지 확인했다.
- 다음에는: `.env*`를 보거나 고칠 일이 있으면 사람에게 부탁하고, 수정 명령에는 섞지 않는다.

## 셸 `cat >`로 새 파일을 만들다 같은 이름의 기존 파일을 덮었다 (2026-11-24, feat/evidence-based-ai-learning)

- 상황: Claude Code가 요약본 DTO를 `cat > dto/MaterialSummaryResponse.java <<EOF`로 만들었는데, 자료 목록 DTO가 이미 그 이름이라 덮어써져 컴파일이 깨졌다.
- 원인: 셸 리다이렉션은 덮어쓰기 전에 파일을 확인하지 않는다(편집 도구는 읽지 않은 파일을 덮지 못하게 막는다).
- 해결: `git show HEAD:<경로>`로 원래 파일을 되살리고 새 DTO는 다른 이름(`StudySummaryResponse`)으로 만들었다.
- 다음에는: 새 파일은 Write 도구로 만들거나, 셸로 만들 때는 `ls`로 같은 이름이 없는지 먼저 본다. 컴파일 오류가 "없는 메서드"로 나면 덮어쓴 파일부터 의심한다.

## 워크트리에서 미리보기 서버를 띄우면 원래 체크아웃의 화면이 뜬다 (2026-11-25, #212)

- 상황: 다른 PR이 메인 체크아웃을 쓰고 있어 `.claude/worktrees/` 아래 워크트리에서 고친 뒤, Claude 미리보기(`preview_start` `frontend`)로 확인했더니 고치기 전 문구가 보였다.
- 원인: 미리보기는 메인 체크아웃의 `.claude/launch.json`을 읽고 그 폴더 기준(`--prefix frontend`)으로 서버를 띄운다. 워크트리의 코드가 아니다.
- 해결: 메인의 `launch.json`에 `cwd`를 워크트리 `frontend`로 둔 임시 설정(다른 포트)을 잠깐 더해 띄우고, 확인 뒤 지웠다. 포트가 다르면 체험 데이터(localStorage)도 따로라 다시 만들어야 한다.
- 다음에는: 워크트리에서 화면을 볼 때는 띄운 뒤 `fetch('/src/...')` 응답이나 브라우저 주소의 파일 경로로 어느 폴더 코드인지 먼저 확인한다.
