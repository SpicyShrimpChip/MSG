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

**백엔드를 배포한 뒤**: `/api` 요청을 백엔드로 넘기는 규칙을 **맨 앞에** 추가하고, Vercel 환경변수 `VITE_USE_MOCK`을 `false`로 바꾼다. 회원가입 자동 가입 방지를 켜려면 Vercel에 `VITE_TURNSTILE_SITE_KEY`(공개 사이트 키), 백엔드에 `TURNSTILE_SECRET_KEY`(비밀 키)를 **같이** 넣는다. 한쪽만 넣으면 가입이 막히거나(백엔드만) 확인 없이 가입된다(프론트만). 키는 Cloudflare 대시보드 Turnstile에서 운영 도메인으로 만든다. 같은 출처로 요청하므로 로그인 쿠키·CORS 설정이 단순해진다(`frontend/CLAUDE.md` "백엔드 연동").

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

- 백엔드 주소는 EC2 탄력적 IP의 sslip.io 주소다(아래 "백엔드 배포" 3단계). 사용자에게는 보이지 않는다.
- Vercel은 외부 백엔드의 **첫 응답을 120초까지만** 기다린다. 그래서 AI를 기다리는 요청의 서버 시간 예산은 100초, 프론트 timeout은 115초다(#197). 프록시가 낸 504는 화면이 시간 초과와 같게 다뤄 결과를 다시 확인한다.

# 백엔드 배포 (AWS EC2, 11/23 확정)

## 구성

```
브라우저 ──▶ Vercel (lms-seven-tawny.vercel.app: 화면 + /api rewrite)
                │ HTTPS
                ▼
           EC2 한 대 (AWS 시드니 ap-southeast-2, t3.small 2GB)
           └ Docker Compose (deploy/compose.yaml)
               caddy  :80·443  자동 HTTPS(sslip.io 주소), /api만 넘김, 그 밖은 404
               backend :8080   GHCR 이미지(backend/Dockerfile), prod 프로필
               db      :5432   PostgreSQL 16 + pgvector (밖으로 열지 않음)
```

- **주소는 Vercel 하나.** 브라우저는 같은 출처로 `/api`를 부르므로 로그인 쿠키(SameSite=Strict)·CORS가 단순하다.
- **RDS·S3는 쓰지 않는다.** DB·업로드 파일은 도커 볼륨(`pgdata`·`uploads`)에 두고 매일 백업한다. 비용과 설정을 줄이려는 선택이다.
- **시드니 리전:** AWS 무료 플랜은 가입 때 정해진 리전 하나만 열린다. 한국에서 요청마다 0.1~0.15초쯤 더 걸린다. 처리방침 7·8번에 적었다.
- **이미지:** main에 백엔드 변경이 머지되면 CI(`backend-ci.yml`의 `docker-publish`)가 테스트 통과 뒤 `ghcr.io/spicyshrimpchip/msg-backend:latest`와 `:sha-<7자리>`로 올린다. 서버에서는 빌드하지 않는다(2GB에서 Gradle 빌드와 DB를 같이 돌리면 메모리가 모자랄 수 있다).

## 처음 띄우기

AWS 콘솔 작업·비밀값 입력은 사람이 한다. 순서대로 하면 30분쯤 걸린다.

1. **EC2 만들기** (리전: 아시아 태평양(시드니))
   - AMI: Ubuntu Server 24.04 LTS (x86_64). 이미지가 x86용이라 Graviton(t4g)은 고르지 않는다
   - 인스턴스 유형: `t3.small`(2GB). 스토리지: gp3 20GB
   - 키 페어: 새로 만들어 내려받은 `.pem`은 커밋·공유하지 않는다
   - 보안 그룹: SSH(22)는 **내 IP만**, HTTP(80)·HTTPS(443)는 모든 곳. 80은 인증서 발급에 필요하다
2. **탄력적 IP** 할당 → 인스턴스에 연결. 인스턴스를 지워도 탄력적 IP는 남아 요금이 나오므로 같이 해제한다
3. **백엔드 주소:** 탄력적 IP가 `3.25.10.20`이면 `3-25-10-20.sslip.io` (sslip.io는 주소 이름을 IP로 바로 풀어 주는 무료 DNS)
4. **서버 준비** (SSH 접속 뒤)
   ```bash
   sudo apt update && sudo apt install -y docker.io docker-compose-v2 git
   sudo usermod -aG docker ubuntu && exit   # 다시 접속하면 sudo 없이 docker를 쓴다
   # 메모리 2GB라 스왑 2GB를 둔다
   sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile
   echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
   git clone https://github.com/SpicyShrimpChip/MSG.git && cd MSG/deploy
   ```
5. **비밀값** (값은 채팅·문서·커밋에 남기지 않는다)
   ```bash
   cp env.example .env && cp backend.env.example backend.env && chmod 600 .env backend.env
   nano .env          # BACKEND_HOST, DB 비밀번호 3개
   nano backend.env   # JWT_SECRET, AI 키·모델, TURNSTILE_SECRET_KEY, REJOIN_HASH_SECRET, KAKAO_*
   ```
6. **이미지 받을 권한:** CI가 처음 올린 패키지는 비공개다. GitHub 조직 → Packages → `msg-backend` → Package settings → Change visibility → **Public**(공개 레포라 코드와 같은 수준, 이미지에 비밀값 없음). 비공개로 두려면 서버에서 `docker login ghcr.io`(패키지 읽기 권한만 있는 토큰)
7. **띄우기:** `./deploy.sh` → "백엔드가 응답해요"가 나오면 바깥에서 확인: `curl https://<BACKEND_HOST>/api/notices`
8. **백업 예약:** `crontab -e`에 `0 4 * * * /home/ubuntu/MSG/deploy/backup.sh >> /home/ubuntu/msg-backup.log 2>&1`
9. **Vercel 연결:** `frontend/vercel.json`에 위 `/api` rewrite(주소는 3단계)를 맨 앞에 넣는 PR, Vercel 환경변수 `VITE_USE_MOCK=false`·`VITE_TURNSTILE_SITE_KEY`, Cloudflare Turnstile 위젯 호스트에 운영 주소 확인 → 다시 배포
10. 아래 **배포 뒤 점검표**

## 업데이트와 되돌리기

- 백엔드 변경이 main에 머지되고 CI가 끝나면 서버에서 `cd ~/MSG && git pull && cd deploy && ./deploy.sh`. DB·업로드 파일·인증서는 볼륨에 있어 그대로 남는다.
- 새 이미지에 문제가 있으면 `.env`에 `BACKEND_IMAGE=ghcr.io/spicyshrimpchip/msg-backend:sha-<이전 커밋 7자리>`를 넣고 `./deploy.sh`. 고친 뒤 그 줄을 지운다. **DB 마이그레이션이 들어간 변경은 이미지만 되돌리면 안 맞을 수 있다**(백업에서 복원).

## 배포 뒤 점검표

- [ ] 가입(Turnstile) → 로그인 → 새로고침해도 로그인 유지(Refresh 쿠키) → 로그아웃
- [ ] 접속 IP: `docker compose logs backend`에서 로그인 실패를 여러 번 내 본 뒤, 다른 기기(휴대폰 데이터)에서는 막히지 않는지
- [ ] 자료 업로드 15MB 안팎(Vercel 전달에서 크기 제한이 있는지 문서로 확인하지 못함)
- [ ] 분석: 세 모델 결과와 걸린 시간(100초 안), 퀴즈 만들기·주관식 채점
- [ ] 탈퇴 → 같은 이메일 재가입이 30일 막히는지
- [ ] `https://<BACKEND_HOST>/swagger-ui/index.html`이 404인지
- [ ] 다음 날 `deploy/backups/`에 백업 파일이 생겼는지

## 백엔드 환경변수

`deploy/backend.env`(백엔드만 읽음)와 `deploy/.env`(compose가 읽음)에 넣는다. DB 접속 주소·계정 이름은 `compose.yaml`이 넣어 준다.

| 묶음 | 환경변수 (값은 쓰지 않는다) |
| ---- | --------------------------- |
| `.env` | `BACKEND_HOST`, `POSTGRES_PASSWORD`(관리자, 백업·복원용), `APP_DB_PASSWORD`(앱 계정), `MIGRATION_DB_PASSWORD`(테이블 변경 계정). 두 계정 비밀번호는 처음 띄울 때 DB에 그대로 만들어진다 |
| 보안 | `JWT_SECRET`, `CORS_ALLOWED_ORIGINS`(Vercel 주소) |
| AI | `OPENAI_API_KEY`·`OPENAI_MODEL`, `ANTHROPIC_API_KEY`·`ANTHROPIC_MODEL`, `GEMINI_API_KEY`·`GEMINI_MODEL` (키나 모델 이름이 없으면 그 모델은 "미설정"), `AI_DAILY_CALL_LIMIT` (서비스 전체 하루 모델 호출 상한, 기본 500, 0이면 상한 없음. 제공사 콘솔 지출 한도와 함께 정한다), `AI_USER_DAILY_CALL_LIMIT` (사용자 한 명의 하루 모델 호출 몫, 분석·퀴즈·채점·개념 설명 합계, 기본 40, 0이면 없음. 계정 몇 개로 전체 상한을 다 쓰지 못하게 전체보다 훨씬 작게 둔다) |
| 가입 | `TURNSTILE_SECRET_KEY` (Cloudflare Turnstile 비밀 키. **운영 프로필은 필수**라 비어 있으면 백엔드가 뜨지 않는다. 프론트 `VITE_TURNSTILE_SITE_KEY`와 같이 켠다). 같은 IP의 가입 시도는 1시간 10번까지다(`app.auth.signup-attempt`, 이메일·카카오 가입 합계. 접속 IP가 정확해야 하므로 아래 점검표의 접속 IP 확인을 같이 본다) |
| 카카오 로그인 | `KAKAO_REST_API_KEY`·`KAKAO_CLIENT_SECRET` (카카오 개발자 콘솔의 REST API 키와 카카오 로그인용 Client Secret. 둘 중 하나나 `KAKAO_REDIRECT_URI`가 비면 카카오 버튼이 숨는다), `KAKAO_ADMIN_KEY` (탈퇴할 때와 가입을 거절할 때 카카오 연결 끊기에 쓴다. **카카오를 켜면 꼭 넣는다**: 비면 연결 끊기를 건너뛰어, 처리방침·약관의 "탈퇴하면 카카오 연결도 끊어요"와 어긋난다), `KAKAO_REDIRECT_URI` (`https://lms-seven-tawny.vercel.app/api/auth/kakao/callback`, 카카오 콘솔에 등록한 주소와 글자까지 같게), `FRONTEND_BASE_URL` (카카오 로그인이 끝나면 돌아갈 화면 주소, Vercel 주소) |
| 재가입 제한 | `REJOIN_HASH_SECRET` (탈퇴한 이메일의 HMAC 키, 32자 이상 무작위 값. 비우면 탈퇴 뒤 30일 재가입 제한이 꺼진다. 운영 중에는 바꾸지 않는다) |
| 선택 | `MATERIAL_CONTENT_ENABLED` (기본 꺼짐. 처리방침에 확정한 뒤에만 켠다) |

- 이미지는 `prod` 프로필로 뜨고 root가 아닌 계정으로 실행한다. 쓸 수 있는 곳은 업로드 폴더 `/data/uploads`(볼륨 `uploads`)뿐이다.
- 백엔드 컨테이너 메모리 한도는 1GB이고 JVM은 그 75%까지 쓴다. DB는 나머지를 쓴다.
- DB 계정 두 개와 `vector` 확장은 `deploy/db-init/01-create-app-db.sh`가 **처음 띄울 때 한 번** 만든다(`backend/db/setup-local-postgres.sh`·CI와 같은 구성). 테이블은 백엔드가 처음 뜰 때 Flyway가 만들고, 운영 프로필은 샘플 데이터를 넣지 않는다.
- 로그인·갱신·로그아웃 API는 `X-Requested-With: XMLHttpRequest` 헤더가 있어야 받는다(프론트 axios가 붙인다). curl로 확인할 때도 이 헤더를 넣는다.
- 운영에서는 Swagger(`/swagger-ui`, `/v3/api-docs`)를 끈다(`application-prod.yaml`, Caddy도 `/api` 밖은 404). API 확인은 로컬에서 한다.

## 실제 접속 IP

요청은 브라우저 → Vercel → Caddy → 백엔드로 온다. Vercel이 `X-Forwarded-For`에 브라우저 IP를 넣어 보내고, Caddy가 그 첫 값 하나만 `X-Forwarded-For`로 넘기며(`Caddyfile`의 `trusted_proxies`·`header_up`), 백엔드는 `server.forward-headers-strategy: native`로 도커 사설망에서 온 이 값을 `getRemoteAddr()`로 쓴다. 이 설정이 없으면 모든 사용자가 Caddy의 IP로 보여 로그인 시도 제한(같은 IP 50번)이 전체에 같이 걸린다.

## 백업과 복원

- `deploy/backup.sh`가 매일 DB(`pg_dump` custom 형식)와 업로드 파일(tar)을 `deploy/backups/`에 남기고 **7일 지난 것은 지운다.** 백업에도 개인정보가 있으므로 서버 밖으로 옮기지 않는다. 탈퇴한 사용자의 정보도 백업에서 7일 안에 지워진다(처리방침 5번).
- 서버(디스크)가 통째로 망가지면 백업도 같이 잃는다. 시연 직전 등 중요한 시점에는 EBS 스냅샷을 수동으로 하나 만든다(콘솔 → EC2 → 볼륨 → 스냅샷 생성).
- **복원** (같은 서버나 새 서버, 백엔드를 멈춘 상태에서):
  ```bash
  cd ~/MSG/deploy
  docker compose stop backend
  docker compose exec -T db pg_restore -U postgres -d msg --clean --if-exists < backups/db-<날짜>.dump
  docker compose run --rm --no-deps -T --entrypoint sh backend -c 'tar -C /data/uploads -xzf -' < backups/uploads-<날짜>.tar.gz
  ./deploy.sh
  ```

## 다른 서버·AWS 계정으로 옮기기

EC2는 계정 사이에 그대로 넘길 수 없어서, 새 서버를 띄우고 데이터를 옮긴다.

1. 옛 서버에서 `./backup.sh`, 백업 두 파일을 새 서버로 복사(`scp`, 내 PC를 거쳐도 된다. 옮긴 뒤 PC의 사본은 지운다)
2. 새 서버에서 "처음 띄우기" 1~6단계. **`.env`·`backend.env`의 값은 옛 서버와 같게**(특히 `JWT_SECRET`이 바뀌면 모두 로그아웃되고, `REJOIN_HASH_SECRET`이 바뀌면 재가입 제한 대조가 깨진다)
3. `docker compose up -d db` → 위 **복원** → `./deploy.sh`
4. 새 탄력적 IP라면 `vercel.json`의 rewrite 주소를 바꾸는 PR. 같은 리전이면 탄력적 IP를 계정 사이에 넘길 수도 있다(주소 유지)
5. 리전·국가가 바뀌면 처리방침 7·8번을 같은 PR에서 고친다
6. 옛 서버의 인스턴스·탄력적 IP·볼륨·스냅샷 삭제

## 비용 관리

- 예산 알림(월 10달러)과 크레딧 잔액을 주마다 본다. 인스턴스를 **중지**해도 디스크(EBS)와 연결 안 된 탄력적 IP는 요금이 나온다.
- AI 요금은 AWS가 아니라 제공사(OpenAI·Anthropic·Google) 콘솔에서 나간다. 지출 한도는 제공사 콘솔과 `AI_DAILY_CALL_LIMIT`로 함께 건다.
