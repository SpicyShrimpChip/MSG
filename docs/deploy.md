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
