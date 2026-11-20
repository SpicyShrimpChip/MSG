# 프론트 배포 (Vercel)

프론트(`frontend/`)는 Vercel에 올려 링크로 바로 볼 수 있게 한다. 백엔드가 아직 배포되지 않아 **목업 모드**로 올린다(로그인·서버 없이 모든 화면이 뜬다). 링크가 공개되어도 화면에 나오는 것은 `src/mocks/`의 예시 데이터뿐이다.

## 지금 연결 상태

- 운영 주소: https://lms-seven-tawny.vercel.app/ (목업 모드)
- GitHub `SpicyShrimpChip/MSG`의 `main`에 연결되어 있다. main에 머지되면 운영 주소가 자동으로 새로 배포되고, PR마다 미리보기 주소가 달린다(PR 화면에 Vercel 봇이 링크를 단다)
- 목업의 퀴즈 결과·자료 목록 변경은 보는 사람 브라우저(localStorage)에만 남는다. 다른 사람이 링크로 들어오면 처음 상태부터 보인다
- **주의:** 무료 플랜은 조직의 비공개 레포를 배포하지 못한다. 레포를 다시 비공개로 바꾸면 자동 배포가 멈춘다
- 서비스 이름이 정해지면: 프로젝트 이름은 Settings → General에서 바꾸고, 주소는 자동으로 바뀌지 않으므로 Settings → Domains에서 `<새 이름>.vercel.app`을 추가한다

## 빌드 설정 (다시 연결할 때)

| 항목                       | 값                                                                   |
| -------------------------- | -------------------------------------------------------------------- |
| Root Directory             | `frontend`                                                           |
| Framework Preset           | Vite (자동으로 잡힌다)                                               |
| Install Command            | `npm ci`                                                             |
| Build Command              | `npm run build`                                                      |
| Output Directory           | `dist`                                                               |
| Node.js Version (Settings) | 24.x (CI와 같게)                                                     |
| 환경변수 `VITE_USE_MOCK`   | `true` (Production·Preview 모두)                                     |
| 환경변수 `HUSKY`           | `0` (설치할 때 커밋 훅을 설치하지 않게. 배포 서버에는 `.git`이 없다) |

- `.env.development`는 개발 서버에서만 읽힌다. 배포 빌드에는 쓰이지 않으므로 `VITE_USE_MOCK`은 Vercel 환경변수로 꼭 넣는다. 빠지면 화면이 `/api`를 실제로 부르고, 아직 백엔드가 없어 오류가 난다.
- 비밀값(API 키 등)은 프론트 환경변수에 넣지 않는다. `VITE_`로 시작하는 값은 빌드 결과에 그대로 들어가 누구나 볼 수 있다.

## `frontend/vercel.json`

React Router로 화면을 나누기 때문에 `/analysis`처럼 바로 들어오거나 새로고침한 주소도 `index.html`을 돌려줘야 한다(없으면 Vercel이 404를 낸다). 실제 파일(`/assets/...`)이 있으면 그 파일이 먼저 나가고, 없을 때만 `index.html`로 넘긴다.

**백엔드를 배포한 뒤**: `/api` 요청을 백엔드로 넘기는 규칙을 **맨 앞에** 추가하고, Vercel 환경변수 `VITE_USE_MOCK`을 `false`로 바꾼다. 같은 출처로 요청하므로 로그인 쿠키·CORS 설정이 단순해진다(`frontend/CLAUDE.md` "백엔드 연동").

```json
{
  "$schema": "https://openapi.vercel.sh/vercel.json",
  "rewrites": [
    {
      "source": "/api/:path*",
      "destination": "https://<백엔드 주소>/api/:path*"
    },
    { "source": "/(.*)", "destination": "/index.html" }
  ]
}
```

- TODO(미결): 백엔드 배포 주소 (`docs/frontend-requests.md` 5번 "배포")

# 백엔드 이미지 (Docker)

호스팅을 어디로 정하든(서버·데스크탑·PaaS) `backend/Dockerfile`로 만든 같은 이미지를 실행한다. 호스팅은 아직 미결이다(루트 `CLAUDE.md` "AWS 세부 서비스").

```bash
docker build -t msg-backend backend
docker run -d -p 8080:8080 --env-file <운영 환경변수 파일> -v msg-uploads:/data/uploads msg-backend
```

- 이미지는 `prod` 프로필로 뜬다. 비밀값은 이미지에 넣지 않고(`backend/.dockerignore`가 `.env`를 뺀다) 실행할 때 환경변수로만 넘긴다. 환경변수 파일도 커밋하지 않는다.
- root가 아닌 계정으로 실행한다. 쓸 수 있는 곳은 업로드 원본 폴더 `/data/uploads`뿐이고, **다시 배포해도 남도록 볼륨이나 호스트 폴더에 연결한다.**
- 테스트는 이미지를 만들 때 돌리지 않는다(CI의 `build-and-test`·`postgres-test`가 맡는다). CI의 `docker-build`는 이미지가 만들어지는지만 확인하고 어디에도 올리지 않는다.
- 메모리는 컨테이너 한도의 75%까지 JVM이 쓴다(`-XX:MaxRAMPercentage=75`). 같은 서버에 DB도 띄우면 2GB 이상을 권한다.

| 묶음 | 환경변수 (값은 쓰지 않는다)                                                                                                                          |
| ---- | ---------------------------------------------------------------------------------------------------------------------------------------------------- |
| DB   | `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`(앱 계정), `DB_MIGRATION_USERNAME`, `DB_MIGRATION_PASSWORD`(테이블 변경 계정)                                  |
| 보안 | `JWT_SECRET`, `CORS_ALLOWED_ORIGINS`                                                                                                                 |
| AI   | `OPENAI_API_KEY`·`OPENAI_MODEL`, `ANTHROPIC_API_KEY`·`ANTHROPIC_MODEL`, `GEMINI_API_KEY`·`GEMINI_MODEL` (키나 모델 이름이 없으면 그 모델은 "미설정"), `AI_DAILY_CALL_LIMIT` (서비스 전체 하루 모델 호출 상한, 기본 500, 0이면 상한 없음. 제공사 콘솔 지출 한도와 함께 정한다) |
| 선택 | `MATERIAL_CONTENT_ENABLED` (기본 꺼짐. 처리방침에 확정한 뒤에만 켠다)                                                                                |

- DB는 PostgreSQL 16 + pgvector이고 계정 두 개가 필요하다. 만드는 SQL은 `.github/workflows/backend-ci.yml`의 "테스트 DB·계정 만들기"와 `backend/db/setup-local-postgres.sh`가 같은 구성이다. `vector` 확장은 관리자 계정으로 미리 설치한다. 테이블은 서버가 처음 뜰 때 Flyway가 만들고, 운영 프로필은 샘플 데이터를 넣지 않는다.
- 로그인·갱신·로그아웃 API는 `X-Requested-With: XMLHttpRequest` 헤더가 있어야 받는다(프론트 axios가 붙인다). curl로 확인할 때도 이 헤더를 넣는다.
- TODO(미결): 프록시 뒤의 실제 접속 IP(`server.forward-headers-strategy`, `AuthController`)는 배포 구성을 정한 뒤 설정한다.
