# 카카오 로그인 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 기존 이메일·비밀번호 로그인은 그대로 두고, 카카오 계정으로 가입·로그인·탈퇴(연결 끊기)를 할 수 있게 한다.

**Architecture:** 카카오 REST "인가 코드" 흐름을 백엔드가 모두 처리한다(브라우저 → `/api/auth/kakao/start` → 카카오 → `/api/auth/kakao/callback`). 이미 연결된 사용자는 기존 세션 발급(`RefreshTokenService.startSession` + Refresh 쿠키)을 그대로 쓰고, 처음 온 사용자는 DB에 아무것도 쓰지 않고 서명한 짧은 쿠키만 준 뒤 프론트의 "가입 마무리" 화면에서 약관 동의를 받아 그때 계정을 만든다. 프론트는 버튼·가입 마무리·로그인 완료 처리 화면만 더한다.

**Tech Stack:** Spring Boot 3 / Java 17, Spring Security, JJWT(이미 사용 중), `RestClient`, Flyway, H2·PostgreSQL 테스트 / React 19, TanStack Query, Zustand, Vitest

**Spec:** 별도 스펙 문서 없음. 11/24 대화에서 정한 내용을 아래 "결정 사항"에 옮겼다(메모리 `signup-kakao-decision`과 같은 내용).

## 결정 사항 (스펙)

- 카카오 앱: 개인 개발자 비즈 앱. 동의 항목은 **이메일(필수)**, **닉네임(선택)** 두 개만 쓴다. 대표 도메인 `https://lms-seven-tawny.vercel.app`.
- Redirect URI(카카오 콘솔에 사용자가 직접 등록): `https://lms-seven-tawny.vercel.app/api/auth/kakao/callback`, `http://localhost:5173/api/auth/kakao/callback`.
- 비밀번호 로그인은 유지. 소셜 전용으로 갈지는 주미와 미정이므로 둘 다 되게 만든다.
- 카카오 정책: ① 가입 마무리 전에는 카카오에서 받은 정보를 DB에 저장하지 않는다 ② 탈퇴하면 카카오 연결 끊기(unlink) ③ 키는 환경변수로만 ④ 서비스 이름·버튼 밖 문구에 "카카오"를 서비스명처럼 쓰지 않는다 ⑤ 버튼은 카카오 디자인 가이드를 따른다.
- 같은 이메일로 이미 가입한 사용자: 카카오가 **확인된 이메일**(`is_email_valid && is_email_verified`)을 주면 그 계정에 카카오를 연결하고 로그인한다. 확인 안 된 이메일이면 막는다.
- 탈퇴 뒤 30일 재가입 제한(`RejoinRestriction`)은 카카오 가입에도 똑같이 건다.
- 서버에 카카오 키가 없으면(로컬·테스트·CI) 카카오 로그인을 숨긴다.

## Global Constraints

- 비밀값(`KAKAO_REST_API_KEY`, `KAKAO_CLIENT_SECRET`, `KAKAO_ADMIN_KEY`)은 환경변수·`backend/.env`·EC2 `backend.env`에만. 레포·테스트·문서 예시에 실제 값을 쓰지 않는다(공개 레포).
- 테스트는 실제 카카오 서버를 부르지 않는다. `SignupBotCheckTest`처럼 `com.sun.net.httpserver.HttpServer`로 로컬 가짜 서버를 띄운다.
- 마이그레이션 번호: `fix/double-submit`(#222)가 `V2__material_request_key.sql`을 넣으므로 이 작업은 **V3**. 시작할 때 `main`의 마지막 번호를 확인하고 그다음 번호를 쓴다.
- 쿠키를 쓰는 POST는 기존처럼 `X-Requested-With: XMLHttpRequest`를 요구한다(`AuthController.requireXmlHttpRequest`).
- UI 문구·주석·문서는 한국어, 식별자는 영어. `dangerouslySetInnerHTML` 금지.
- 응답 형식이 바뀌는 곳(`UserResponse.hasPassword`)은 백엔드 DTO와 프론트 `types/auth.ts`를 같은 PR에서 고친다.
- 작업 끝에 `./gradlew test`, `./gradlew pgTest`(로컬 PostgreSQL), `frontend`의 `npm run lint && npm run build && npm test`가 통과해야 한다.
- PR은 둘로 나눈다: **PR 1 백엔드(Task 1~6)**, **PR 2 프론트(Task 7~9)**. 개인정보 처리방침·약관 문구(Task 10)는 주미 검토용 **PR 3**.

## Review Focus

1. **Refresh 쿠키(SameSite=Strict)가 카카오에서 돌아온 리다이렉트 뒤에도 살아 있는가.** 콜백 응답에서 쿠키를 심고 프론트 `/auth/social/done`이 `fetch`로 `/api/auth/refresh`를 부르면 로그인이 이어져야 한다. → Task 9의 실제 브라우저 확인에서 크롬으로 직접 본다(단위 테스트로 못 잡음).
2. **`returnTo` 열린 리다이렉트.** `//evil.com`, `/\evil.com`, `https://evil.com`, `javascript:` 같은 값은 `/`로 바뀌어야 한다. → Task 2 `ReturnToTest`, Task 7 `safeReturnTo` 테스트.
3. **가입 마무리 쿠키 위조·재사용.** 서명이 틀리거나 10분이 지났거나 Access Token을 대신 넣으면 401. 가입 성공 뒤에는 쿠키를 지운다. 같은 카카오 계정으로 두 탭에서 동시에 가입하면 하나만 성공(409). → Task 3·5 테스트.
4. **비밀번호 없는 사용자.** 비밀번호 로그인은 "이메일 또는 비밀번호를 확인해 주세요."로 똑같이 실패(가입 방식 노출 금지), 탈퇴는 `"탈퇴"` 입력으로. → Task 6 테스트.
5. **카카오 서버 장애.** 토큰·사용자 정보 요청이 실패하거나 느리면(5초 넘김) `socialError=failed`로 로그인 화면에 돌아오고 DB는 그대로. 연결 끊기 실패는 탈퇴를 막지 않는다. → Task 4·6 테스트.

---

### Task 1: 설정값과 DB 테이블

**Files:**
- Create: `backend/src/main/java/com/msg/backend/domain/auth/application/KakaoProperties.java`
- Create: `backend/src/main/resources/db/migration/V3__social_account.sql`
- Create: `backend/src/main/java/com/msg/backend/domain/auth/entity/SocialProvider.java`
- Create: `backend/src/main/java/com/msg/backend/domain/auth/entity/UserSocialAccount.java`
- Create: `backend/src/main/java/com/msg/backend/domain/auth/dao/UserSocialAccountRepository.java`
- Modify: `backend/src/main/java/com/msg/backend/domain/user/entity/User.java` (`passwordHash` nullable, `signUpWithSocial`, `hasPassword()`)
- Modify: `backend/src/main/resources/application.yaml` (`app.oauth.kakao`, `app.frontend-base-url`)
- Modify: `backend/.env.example`, `deploy/backend.env.example` (변수 이름만, 값은 비움)
- Test: `backend/src/test/java/com/msg/backend/domain/auth/dao/UserSocialAccountPersistenceTest.java`

**Interfaces:**
- Produces:
  - `record KakaoProperties(String clientId, String clientSecret, String adminKey, String redirectUri, String authBaseUrl, String apiBaseUrl, Duration timeout)` — `@ConfigurationProperties("app.oauth.kakao")`, `boolean enabled()` = clientId·clientSecret·redirectUri가 모두 있으면 true.
  - `record FrontendProperties(String baseUrl)` — `@ConfigurationProperties("app.frontend")`, 기본 `http://localhost:5173`, 끝 `/` 제거.
  - `enum SocialProvider { KAKAO; String apiValue() /* "kakao" */ }`
  - `UserSocialAccount.link(User user, SocialProvider provider, String providerUserId, LocalDateTime now)`
  - `UserSocialAccountRepository`: `Optional<UserSocialAccount> findByProviderAndProviderUserId(SocialProvider, String)`, `List<UserSocialAccount> findAllByUserId(Long)`, `boolean existsByUserIdAndProvider(Long, SocialProvider)`
  - `User.signUpWithSocial(String email, String name, boolean aiAnalysisConsent, LocalDateTime now)` (passwordHash = null), `boolean User.hasPassword()`

- [ ] **Step 1: 실패하는 영속성 테스트 작성** — `@IntegrationTest`로:
  - `비밀번호_없는_사용자를_저장할_수_있다()`: `User.signUpWithSocial(...)` 저장 → 다시 읽으면 `getPasswordHash()`가 null, `hasPassword()`가 false.
  - `같은_카카오_회원번호는_두_번_연결할_수_없다()`: 두 사용자에 `providerUserId="123"` 연결 → 두 번째 `saveAndFlush`에서 `DataIntegrityViolationException`.
  - `한_사용자에_카카오는_하나만_연결된다()`: 같은 사용자에 다른 번호 두 개 → `DataIntegrityViolationException`.
  - `사용자를_지우면_연결도_지워진다()`: 사용자 삭제 뒤 `findAllByUserId`가 빈 목록.
- [ ] **Step 2: `./gradlew test --tests '*UserSocialAccountPersistenceTest'` → 컴파일 실패 확인**
- [ ] **Step 3: 마이그레이션 작성** — V1 스타일(`GENERATED BY DEFAULT AS IDENTITY`, `TIMESTAMP(6)`, 주석 한국어)로:
  - `ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;` (H2·PostgreSQL 모두 되는 문법)
  - `user_social_account(id, user_id NOT NULL, provider VARCHAR(20) NOT NULL, provider_user_id VARCHAR(64) NOT NULL, created_at TIMESTAMP(6) NOT NULL)`, `uk_user_social_account_provider_user UNIQUE(provider, provider_user_id)`, `uk_user_social_account_user_provider UNIQUE(user_id, provider)`, `fk_user_social_account_user ... ON DELETE CASCADE`.
- [ ] **Step 4: 엔티티·리포지토리·설정 레코드 구현**, `application.yaml`에 추가:
  ```yaml
  frontend:
    base-url: ${FRONTEND_BASE_URL:http://localhost:5173}
  oauth:
    kakao:
      client-id: ${KAKAO_REST_API_KEY:}
      client-secret: ${KAKAO_CLIENT_SECRET:}
      admin-key: ${KAKAO_ADMIN_KEY:}
      redirect-uri: ${KAKAO_REDIRECT_URI:}
      auth-base-url: https://kauth.kakao.com
      api-base-url: https://kapi.kakao.com
      timeout: 5s
  ```
  `WithdrawalService` 주석의 CASCADE 목록에 `user_social_account`를 더한다.
- [ ] **Step 5: 테스트 통과 확인** — `./gradlew test --tests '*UserSocialAccountPersistenceTest'` PASS, 그리고 `./gradlew pgTest --tests '*UserSocialAccountPersistenceTest'` PASS(PostgreSQL에서 DROP NOT NULL 확인).
- [ ] **Step 6: 커밋** `feat: 소셜 계정 연결 테이블과 카카오 설정값 추가`

### Task 2: 로그인 시작 — state·returnTo 쿠키

**Files:**
- Create: `backend/src/main/java/com/msg/backend/domain/auth/application/ReturnTo.java`
- Create: `backend/src/main/java/com/msg/backend/domain/auth/api/OAuthStateCookies.java`
- Create: `backend/src/main/java/com/msg/backend/domain/auth/api/SocialAuthController.java` (start·providers만, callback은 Task 4)
- Modify: `backend/src/main/java/com/msg/backend/global/security/SecurityConfig.java` (`PUBLIC_GET`에 `/api/auth/social/providers`, `/api/auth/kakao/start`, `/api/auth/kakao/callback`, `/api/auth/social/pending`)
- Test: `backend/src/test/java/com/msg/backend/domain/auth/application/ReturnToTest.java`, `backend/src/test/java/com/msg/backend/domain/auth/api/SocialAuthApiTest.java`

**Interfaces:**
- Consumes: `KakaoProperties`, `FrontendProperties` (Task 1), `AuthProperties.refreshCookie().secure()` (Secure 여부를 같이 따른다)
- Produces:
  - `static String ReturnTo.sanitize(String raw)` — `/`로 시작하고, 두 번째 글자가 `/`·`\`가 아니고, 제어문자·`\` 없고, 200자 이하일 때만 그대로. 아니면 `"/"`.
  - `OAuthStateCookies`: `String issue(HttpServletResponse res, String returnTo)` (32바이트 SecureRandom → base64url state를 돌려줌), `Optional<String> consume(HttpServletRequest req, HttpServletResponse res, String state)` (맞으면 returnTo, 쿠키는 항상 지움, 비교는 `MessageDigest.isEqual`)
  - 쿠키: 이름 `msg_oauth_state`, 값 `<state>.<base64url(returnTo)>`, `HttpOnly`, `SameSite=Lax`(카카오에서 돌아오는 최상위 GET에 실려야 하므로), `Path=/api/auth/kakao`, `Max-Age=600`.
  - `GET /api/auth/social/providers` → `ApiResponse<List<String>>`, 카카오 설정이 있으면 `["kakao"]`, 없으면 `[]`.
  - `GET /api/auth/kakao/start?returnTo=` → 302 `{authBaseUrl}/oauth/authorize?response_type=code&client_id=..&redirect_uri=..&state=..`. 설정이 없으면 302 `{frontend}/login?socialError=unavailable`.

- [ ] **Step 1: `ReturnToTest` 작성** — `/analysis`·`/quizzes/3?tab=a` 그대로, `null`·`""`·`//evil.com`·`/\evil.com`·`https://evil.com`·`javascript:alert(1)`·`/a\nb`·201자 → `"/"`.
- [ ] **Step 2: `SocialAuthApiTest` 작성** (`@IntegrationTest`, `@TestPropertySource`로 가짜 client-id·secret·redirect-uri):
  - `providers는_카카오를_돌려준다()`
  - `start는_카카오_인가_주소로_보내고_state_쿠키를_심는다()`: 302, `Location`에 `client_id=`·`state=`·`redirect_uri=`, `Set-Cookie`에 `msg_oauth_state`·`HttpOnly`·`SameSite=Lax`·`Path=/api/auth/kakao`, Location의 state와 쿠키 앞부분이 같다.
  - 설정 없는 경우는 `SocialAuthUnavailableTest`(속성 없이)로: providers `[]`, start → `/login?socialError=unavailable`.
- [ ] **Step 3: 실패 확인** — `./gradlew test --tests '*ReturnToTest' --tests '*SocialAuth*'`
- [ ] **Step 4: 구현**
- [ ] **Step 5: 통과 확인** (같은 명령), `UnauthenticatedAccessTest`도 같이 돌려 공개 주소가 늘어난 것 외에 바뀐 게 없는지 본다.
- [ ] **Step 6: 커밋** `feat: 카카오 로그인 시작 주소와 state 확인 쿠키`

### Task 3: 카카오 API 클라이언트와 가입 마무리 표(티켓)

**Files:**
- Create: `backend/src/main/java/com/msg/backend/domain/auth/application/KakaoClient.java`
- Create: `backend/src/main/java/com/msg/backend/domain/auth/application/SocialProfile.java`
- Create: `backend/src/main/java/com/msg/backend/domain/auth/application/SocialSignupTicket.java`
- Modify: `backend/src/main/java/com/msg/backend/global/security/jwt/JwtTokenProvider.java` (티켓 서명·검증 메서드 2개)
- Test: `backend/src/test/java/com/msg/backend/domain/auth/application/KakaoClientTest.java`, `backend/src/test/java/com/msg/backend/domain/auth/application/SocialSignupTicketTest.java`, `backend/src/test/java/com/msg/backend/support/FakeKakaoServer.java`

**Interfaces:**
- Consumes: `KakaoProperties`
- Produces:
  - `record SocialProfile(SocialProvider provider, String providerUserId, String email, boolean emailVerified, String nickname)` — nickname은 null 가능, email은 null 가능(동의 안 했거나 없는 계정).
  - `KakaoClient.fetchProfile(String code) -> SocialProfile` — ① `POST {authBaseUrl}/oauth/token` 폼 `grant_type=authorization_code, client_id, client_secret, redirect_uri, code` ② `GET {apiBaseUrl}/v2/user/me` `Authorization: Bearer {access_token}` → `id`, `kakao_account.email`, `is_email_valid && is_email_verified`, `kakao_account.profile.nickname`. 카카오 access token은 쓰고 버린다(저장 안 함). 실패·시간 초과는 `SocialLoginException(SocialError.FAILED)`.
  - `KakaoClient.unlink(String providerUserId) -> void` — `POST {apiBaseUrl}/v1/user/unlink`, 헤더 `Authorization: KakaoAK {adminKey}`, 폼 `target_id_type=user_id, target_id=..`. adminKey가 비면 아무것도 안 하고 경고 로그.
  - `enum SocialError { STATE, CANCELLED, EMAIL_REQUIRED, EMAIL_UNVERIFIED, REJOIN_RESTRICTED, LINK_CONFLICT, FAILED, UNAVAILABLE; String code() /* 소문자 */ }`, `class SocialLoginException extends RuntimeException { SocialError error(); }`
  - `JwtTokenProvider.createSocialSignupTicket(SocialProfile p) -> String`, `JwtTokenProvider.parseSocialSignupTicket(String token) -> Optional<SocialProfile>` — 같은 키, `aud="social-signup"`, 클레임 `prv, pid, email, nick`, 만료 10분, `sid` 없음(그래서 Access Token 검증에서 자동으로 거절된다). 확인된 이메일인 프로필만 티켓으로 만든다.
  - `SocialSignupTicket` = 쿠키 읽기·쓰기·지우기(이름 `msg_social_signup`, `HttpOnly`, `SameSite=Lax`, `Path=/api/auth/social`, `Max-Age=600`).

- [ ] **Step 1: `FakeKakaoServer` 작성** — `HttpServer`로 `/oauth/token`, `/v2/user/me`, `/v1/user/unlink`를 흉내. 다음 응답·상태 코드·지연을 테스트에서 바꿀 수 있고, 마지막 요청의 폼·헤더를 기록한다.
- [ ] **Step 2: `KakaoClientTest` 작성**:
  - `코드로_프로필을_받는다()`: 폼에 `client_secret`·`redirect_uri`·`code`가 실리고, 결과 `providerUserId="4242"`, email, `emailVerified=true`, nickname.
  - `이메일_확인이_안_됐으면_emailVerified가_false다()`
  - `이메일이_없으면_email이_null이다()`
  - `토큰_요청이_400이면_FAILED()`, `사용자_정보가_5초를_넘기면_FAILED()`(timeout을 1초로 두고 2초 지연)
  - `unlink는_어드민_키와_회원번호를_보낸다()`: 헤더 `KakaoAK test-admin`, 폼 `target_id=4242`.
- [ ] **Step 3: `SocialSignupTicketTest` 작성**:
  - `만든_티켓을_그대로_읽는다()`
  - `10분이_지나면_읽지_못한다()` (`MutableClock` 10분 1초 진행)
  - `서명을_바꾸면_읽지_못한다()`
  - `Access_Token은_티켓으로_읽지_못하고_티켓은_Access_Token으로_읽지_못한다()`
- [ ] **Step 4: 실패 확인** `./gradlew test --tests '*KakaoClientTest' --tests '*SocialSignupTicketTest'`
- [ ] **Step 5: 구현** — `RestClient`는 `SignupBotCheck`와 같은 방식(`JdkClientHttpRequestFactory`, connect 5초, read = `timeout`).
- [ ] **Step 6: 통과 확인** (같은 명령)
- [ ] **Step 7: 커밋** `feat: 카카오 API 클라이언트와 가입 마무리용 서명 쿠키`

### Task 4: 콜백 — 로그인·연결·가입 마무리로 보내기

**Files:**
- Create: `backend/src/main/java/com/msg/backend/domain/auth/application/SocialLoginService.java`
- Modify: `backend/src/main/java/com/msg/backend/domain/auth/api/SocialAuthController.java` (callback)
- Test: `backend/src/test/java/com/msg/backend/domain/auth/api/KakaoCallbackApiTest.java`

**Interfaces:**
- Consumes: `KakaoClient.fetchProfile`, `OAuthStateCookies.consume`, `ReturnTo.sanitize`, `JwtTokenProvider.createSocialSignupTicket`, `RefreshTokenService.startSession(Long)`, `JwtTokenProvider.createAccessToken`, `RefreshTokenCookies.write`, `RejoinRestriction.ensureCanJoin(String)`, `UserSocialAccountRepository`
- Produces:
  - `sealed interface SocialLoginOutcome permits LoggedIn, NeedsSignup` — `record LoggedIn(String refreshToken)`, `record NeedsSignup(String ticket)`.
  - `SocialLoginService.handle(SocialProfile profile) -> SocialLoginOutcome` (`@Transactional`):
    1. `(provider, providerUserId)`로 연결이 있으면 → 그 사용자로 세션 시작 → `LoggedIn`.
    2. email이 null → `EMAIL_REQUIRED`. `emailVerified=false` → `EMAIL_UNVERIFIED`.
    3. 같은 email(정규화: strip + lower) 사용자가 있으면: 이미 카카오가 다른 번호로 연결돼 있으면 `LINK_CONFLICT`, 아니면 연결을 저장하고 세션 시작 → `LoggedIn`.
    4. 없으면 `rejoinRestriction.ensureCanJoin(email)`(막히면 `REJOIN_RESTRICTED`) → DB에 쓰지 않고 `NeedsSignup(ticket)`.
  - `GET /api/auth/kakao/callback?code=&state=&error=` 리다이렉트 규칙:
    - state 쿠키가 없거나 다르면 → `{frontend}/login?socialError=state` (카카오를 부르지 않는다)
    - `error=access_denied` → `{frontend}/login?socialError=cancelled`
    - `LoggedIn` → Refresh 쿠키를 심고 `{frontend}/auth/social/done?returnTo=<인코딩>`
    - `NeedsSignup` → `msg_social_signup` 쿠키를 심고 `{frontend}/signup/social?returnTo=<인코딩>`
    - `SocialLoginException` → `{frontend}/login?socialError=<code>`, 그 밖의 예외도 `failed`로 (스택은 로그에만)
  - 응답에 `Cache-Control: no-store`, `Referrer-Policy: no-referrer`(코드가 남지 않게).

- [ ] **Step 1: `KakaoCallbackApiTest` 작성** (`FakeKakaoServer`를 `@DynamicPropertySource`로 연결; start를 먼저 불러 state 쿠키를 얻는 헬퍼):
  - `연결된_사용자는_바로_로그인된다()`: done으로 302, `Set-Cookie`에 Refresh 쿠키, 그 쿠키로 `/api/auth/refresh` 200.
  - `확인된_같은_이메일의_기존_회원은_연결하고_로그인한다()`: 비밀번호 회원 가입 → 콜백 → done, `user_social_account` 1행.
  - `처음_온_사용자는_가입_마무리로_가고_DB는_그대로다()`: users·user_social_account 행 수가 변하지 않는다, `msg_social_signup` 쿠키 있음.
  - `state가_다르면_카카오를_부르지_않는다()`: socialError=state, 가짜 서버 호출 0번.
  - `state_쿠키는_한_번만_쓸_수_있다()`: 같은 쿠키로 두 번째 콜백 → state.
  - `취소하면_cancelled()`, `이메일이_없으면_email_required()`, `확인_안_된_이메일이면_email_unverified()`, `카카오_장애면_failed고_DB는_그대로()`
  - `탈퇴_30일_안의_이메일이면_rejoin_restricted()` (`RejoinRestrictionApiTest`와 같은 설정으로 해시 키 켬)
  - `이미_다른_카카오가_연결된_회원이면_link_conflict()`
  - `returnTo는_정리되어_넘어간다()`: start에 `returnTo=//evil.com` → done의 `returnTo=%2F`.
- [ ] **Step 2: 실패 확인** `./gradlew test --tests '*KakaoCallbackApiTest'`
- [ ] **Step 3: 구현**
- [ ] **Step 4: 통과 확인** (같은 명령)
- [ ] **Step 5: 커밋** `feat: 카카오 콜백에서 로그인·계정 연결·가입 마무리로 나누기`

### Task 5: 가입 마무리 API

**Files:**
- Create: `backend/src/main/java/com/msg/backend/domain/auth/dto/SocialSignupRequest.java`
- Create: `backend/src/main/java/com/msg/backend/domain/auth/dto/PendingSocialSignupResponse.java`
- Modify: `backend/src/main/java/com/msg/backend/domain/auth/application/SocialLoginService.java` (`completeSignup`)
- Modify: `backend/src/main/java/com/msg/backend/domain/auth/api/SocialAuthController.java`
- Modify: `backend/src/main/java/com/msg/backend/global/security/SecurityConfig.java` (`PUBLIC_AUTH`에 `/api/auth/social/signup`)
- Test: `backend/src/test/java/com/msg/backend/domain/auth/api/SocialSignupApiTest.java`

**Interfaces:**
- Consumes: `JwtTokenProvider.parseSocialSignupTicket`, `SocialSignupTicket` 쿠키, `LearningProfileValidator.validate`, `RejoinRestriction.ensureCanJoin`, `User.signUpWithSocial`, `UserSocialAccount.link`, `RefreshTokenService.startSession`
- Produces:
  - `GET /api/auth/social/pending` → `ApiResponse<PendingSocialSignupResponse(String provider, String email, String nickname)>`; 쿠키 없음·만료·위조 → 401 `"가입 정보가 만료됐어요. 카카오 로그인을 다시 해 주세요."`
  - `record SocialSignupRequest(@NotBlank @Size(max=30) String name, @AssertTrue boolean agreedTerms, @AssertTrue boolean agreedPrivacy, boolean agreedAiAnalysis, boolean agreedAiQuiz, String learningPurpose, String jobField, String jobFieldOther)` — 메시지는 `SignupRequest`와 같은 문구.
  - `POST /api/auth/social/signup` (`X-Requested-With` 필수) → `ApiResponse<LoginResponse>`, Refresh 쿠키 심기, `msg_social_signup` 쿠키 지우기. 같은 이메일이 이미 있거나 같은 카카오가 이미 연결됐으면(동시 가입·티켓 재사용) 409 `AuthService.DUPLICATE_EMAIL_MESSAGE`. 재가입 제한은 여기서 한 번 더 확인한다(티켓이 10분 유효하므로).

- [ ] **Step 1: `SocialSignupApiTest` 작성** (Task 4의 헬퍼로 가입 마무리 쿠키를 얻는다):
  - `pending은_카카오_이메일과_닉네임을_보여준다()`
  - `약관에_동의하면_계정이_생기고_로그인된다()`: 200, `accessToken` 있음, `user.hasPassword=false`, 쿠키 지움(`Max-Age=0`).
  - `약관_동의가_없으면_400이고_DB는_그대로()`
  - `쿠키가_없거나_만료되면_401()` (`MutableClock` 11분)
  - `X_Requested_With가_없으면_403()`
  - `같은_쿠키로_두_번_가입하면_두_번째는_409()`
  - `같은_쿠키로_동시에_두_번_가입해도_계정은_하나()` (`CompletableFuture` 두 개 → 상태 `{200,409}`, users 1행)
  - `가입한_계정은_다음_카카오_로그인에서_바로_로그인된다()`
- [ ] **Step 2: 실패 확인** `./gradlew test --tests '*SocialSignupApiTest'`
- [ ] **Step 3: 구현** — 응답 상태는 로그인과 같은 200으로 둔다(가입 직후 로그인까지 하므로). `saveAndFlush`의 `DataIntegrityViolationException`은 409.
- [ ] **Step 4: 통과 확인** (같은 명령)
- [ ] **Step 5: 커밋** `feat: 카카오 가입 마무리 (약관 동의 뒤 계정 만들기)`

### Task 6: 비밀번호 없는 사용자 — 로그인·내 정보·탈퇴·연결 끊기

**Files:**
- Modify: `backend/src/main/java/com/msg/backend/domain/auth/application/AuthService.java` (`login`: 해시가 null이면 더미 비교 후 같은 실패)
- Modify: `backend/src/main/java/com/msg/backend/domain/user/dto/UserResponse.java` (`hasPassword` 추가)
- Modify: `backend/src/main/java/com/msg/backend/domain/user/dto/WithdrawRequest.java`
- Modify: `backend/src/main/java/com/msg/backend/domain/user/application/WithdrawalService.java`
- Modify: `backend/src/main/java/com/msg/backend/domain/user/api/UserController.java` (Swagger 설명)
- Test: `backend/src/test/java/com/msg/backend/domain/user/api/SocialWithdrawalApiTest.java`, `AuthApiTest`에 한 건, `WithdrawalApiTest` 기존 그대로 통과

**Interfaces:**
- Consumes: `UserSocialAccountRepository.findAllByUserId`, `KakaoClient.unlink`
- Produces:
  - `UserResponse`에 `@Schema(description = "비밀번호로 로그인할 수 있는지. false면 탈퇴 때 비밀번호 대신 \"탈퇴\"를 입력한다") boolean hasPassword` (마지막 필드로 추가, `from(User)`에서 `user.hasPassword()`)
  - `record WithdrawRequest(@Size(max=128) String password, @Size(max=10) String confirmText)` — `@NotBlank` 제거.
  - `WithdrawalService.withdraw(Long userId, String password, String confirmText, String clientIp)`:
    - 비밀번호 있는 사용자: 지금과 같다(password 비면 `WRONG_PASSWORD_MESSAGE` 400).
    - 비밀번호 없는 사용자: `"탈퇴".equals(confirmText.strip())`가 아니면 400 `"확인 문구로 \"탈퇴\"를 입력해 주세요."`.
    - 삭제 전에 연결 목록을 읽어 두고, `TransactionSynchronization.afterCommit`에서 `KakaoClient.unlink`를 부른다. 실패는 `log.warn`만(탈퇴는 이미 끝남).

- [ ] **Step 1: 테스트 작성**:
  - `AuthApiTest.비밀번호_없는_계정에_비밀번호_로그인은_같은_문구로_실패한다()` — 401, 메시지 `LOGIN_FAILED_MESSAGE`.
  - `SocialWithdrawalApiTest.카카오_사용자는_탈퇴를_입력해_탈퇴하고_연결이_끊긴다()` — 200, users 0행, `FakeKakaoServer`에 unlink 1번(`target_id` 일치).
  - `탈퇴_문구가_틀리면_400이고_연결은_그대로()` — unlink 0번.
  - `연결_끊기가_실패해도_탈퇴는_된다()` — 가짜 서버 500, 응답 200, users 0행.
  - `비밀번호와_카카오가_둘_다_있으면_비밀번호로_탈퇴하고_연결도_끊긴다()`
  - `내_정보에_hasPassword가_실린다()` — 비밀번호 회원 true, 카카오 회원 false.
- [ ] **Step 2: 실패 확인** `./gradlew test --tests '*SocialWithdrawalApiTest' --tests '*AuthApiTest' --tests '*WithdrawalApiTest'`
- [ ] **Step 3: 구현**
- [ ] **Step 4: 전체 확인** — `./gradlew test` 전부 PASS, `./gradlew pgTest` 전부 PASS.
- [ ] **Step 5: 문서** — `docs/frontend-requests.md`에 카카오 로그인 절(주소·리다이렉트·`socialError` 코드·`hasPassword`·탈퇴 요청 모양), `backend/CLAUDE.md` 인증 절, `docs/deploy.md`와 `deploy/backend.env.example`에 `KAKAO_*`·`FRONTEND_BASE_URL` 설명. 시행착오가 있었다면 `docs/wiki/backend.md`에 한 건.
- [ ] **Step 6: 커밋** `feat: 비밀번호 없는 계정의 로그인·탈퇴와 카카오 연결 끊기` → **여기까지 PR 1 (백엔드)**. ⚠️ PR 본문에 `UserResponse.hasPassword` 추가·`WithdrawRequest` 변경을 적고 주미에게 알린다.

### Task 7: 프론트 API·타입·로그인 화면 버튼

**Files:**
- Modify: `frontend/src/types/auth.ts` (`User.hasPassword`, `PendingSocialSignup`, `SocialSignupInput`)
- Modify: `frontend/src/api/auth.api.ts`
- Create: `frontend/src/utils/auth/socialLogin.ts` (+ `socialLogin.test.ts`)
- Create: `frontend/src/pages/auth/components/KakaoLoginButton.tsx`
- Modify: `frontend/src/pages/auth/LoginPage.tsx`, `frontend/src/pages/auth/SignupPage.tsx`

**Interfaces:**
- Consumes: 백엔드 계약(Task 2·5)
- Produces:
  - `getSocialProviders(): Promise<string[]>`, `getPendingSocialSignup(): Promise<PendingSocialSignup>`, `completeSocialSignup(input: SocialSignupInput): Promise<LoginResult>` (성공하면 `login()`처럼 `setAccessToken` + 체험 종료)
  - `safeReturnTo(raw: string | null | undefined): string` — 백엔드 `ReturnTo.sanitize`와 같은 규칙.
  - `kakaoStartUrl(returnTo?: string): string` → `/api/auth/kakao/start?returnTo=<인코딩>` (axios가 아니라 `window.location.assign`으로 이동)
  - `socialErrorMessage(code: string | null): string | null` — `state`: "로그인 확인 시간이 지났어요. 다시 시도해 주세요.", `cancelled`: null(조용히), `email_required`: "카카오 계정의 이메일 제공에 동의해야 가입할 수 있어요.", `email_unverified`: "카카오 계정의 이메일 인증을 마친 뒤 다시 시도해 주세요.", `rejoin_restricted`: "탈퇴한 이메일은 30일 동안 다시 가입할 수 없어요.", `link_conflict`: "이 이메일에는 다른 카카오 계정이 연결되어 있어요.", `unavailable`·`failed`·그 밖: "카카오 로그인 중 문제가 생겼어요. 잠시 뒤 다시 시도해 주세요."
  - `<KakaoLoginButton returnTo?: string />` — `useQuery(['social-providers'])`로 목록에 `kakao`가 있을 때만 그린다(체험·목업에서 실패하면 숨김). 카카오 디자인 가이드: 배경 `#FEE500`, 심볼 검정 말풍선(SVG 인라인), 글자 `rgba(0,0,0,0.85)` "카카오 로그인", 모서리 12px, 다크 모드에서도 같은 색.

- [ ] **Step 1: `socialLogin.test.ts` 작성** — `safeReturnTo`는 Task 2와 같은 표(`/analysis` 통과, `//evil.com`·`/\evil.com`·`https://x`·`javascript:`·201자 → `/`), `socialErrorMessage('cancelled')`는 null, 모르는 코드는 일반 문구.
- [ ] **Step 2: 실패 확인** `npm --prefix frontend test -- socialLogin`
- [ ] **Step 3: 구현** — LoginPage는 `?socialError=`를 읽어 폼 위 오류 상자로 보여 주고, 비밀번호 폼 아래에 "또는" 구분선 + 버튼. SignupPage에도 같은 버튼(가입도 카카오로 시작 가능).
- [ ] **Step 4: 확인** `npm --prefix frontend run lint && npm --prefix frontend test`
- [ ] **Step 5: 커밋** `feat: 로그인·가입 화면에 카카오 로그인 버튼`

### Task 8: 로그인 완료 처리·가입 마무리 화면

**Files:**
- Create: `frontend/src/pages/auth/SocialDonePage.tsx`
- Create: `frontend/src/pages/auth/SocialSignupPage.tsx`
- Modify: `frontend/src/router/index.tsx` (`auth/social/done`, `signup/social` — 로그인 없이 들어오는 주소, Layout 밖 `/login`과 같은 자리)
- Test: `frontend/src/pages/auth/SocialSignupPage.test.tsx`

**Interfaces:**
- Consumes: `refreshAccessToken()`(`api/axios.ts`), `ME_QUERY_KEY`, `getPendingSocialSignup`, `completeSocialSignup`, `safeReturnTo`, 기존 `SignupAgreements`·`AuthCard`·학습 목적 고르기 컴포넌트
- Produces: 화면 두 개.
  - `SocialDonePage`: 마운트 때 한 번 `refreshAccessToken()` → `queryClient.invalidateQueries(ME_QUERY_KEY)` → `navigate(safeReturnTo(returnTo), { replace: true })`. 실패하면 `/login?socialError=failed`로. 화면에는 "로그인하는 중이에요…"만.
  - `SocialSignupPage`: pending을 읽어 이메일(읽기 전용)·이름(닉네임으로 채움, 30자)·약관 동의(일반 가입과 같은 컴포넌트·문구)·학습 목적(선택)을 받는다. 제출은 `useSingleFlightMutation`(#222)으로 한 번만. pending 401이면 "가입 정보가 만료됐어요" + 로그인으로 가기 버튼. 409면 서버 문구를 보여 주고 로그인 링크.

- [ ] **Step 1: `SocialSignupPage.test.tsx` 작성** (API 함수 모킹):
  - `카카오_이메일과_닉네임을_채워_보여준다`
  - `필수_약관에_동의해야_가입_버튼이_눌린다`
  - `제출하면_completeSocialSignup을_한_번만_부르고_returnTo로_간다` (두 번 클릭)
  - `가입_정보가_만료되면_다시_로그인_안내를_보여준다`
- [ ] **Step 2: 실패 확인** `npm --prefix frontend test -- SocialSignupPage`
- [ ] **Step 3: 구현**
- [ ] **Step 4: 확인** `npm --prefix frontend run lint && npm --prefix frontend run build && npm --prefix frontend test`
- [ ] **Step 5: 커밋** `feat: 카카오 로그인 완료 처리와 가입 마무리 화면`

### Task 9: 마이페이지 탈퇴(비밀번호 없는 사용자)와 실제 카카오로 로컬 확인

**Files:**
- Modify: `frontend/src/api/auth.api.ts` (`deleteAccount(input: { password?: string; confirmText?: string })`)
- Modify: `frontend/src/pages/mypage/MyPage.tsx` (`DeleteAccountSection`)
- Test: `frontend/src/pages/mypage/DeleteAccountSection.test.tsx` (또는 MyPage 테스트에 추가)

**Interfaces:**
- Consumes: `User.hasPassword` (Task 7)
- Produces: `me.hasPassword`가 false면 비밀번호 입력 대신 "확인을 위해 탈퇴라고 입력해 주세요" 칸, 입력이 정확히 `탈퇴`일 때만 버튼 활성. 안내 문구에 "카카오 연결도 함께 끊어요"를 더한다.

- [ ] **Step 1: 테스트 작성** — `비밀번호_없는_사용자는_탈퇴_문구를_입력한다`(`deleteAccount`가 `{ confirmText: '탈퇴' }`로 불림), `비밀번호_사용자는_예전처럼_비밀번호를_입력한다`.
- [ ] **Step 2: 실패 확인 → 구현 → 통과** `npm --prefix frontend test -- DeleteAccount`
- [ ] **Step 3: 실제 카카오로 로컬 확인 (사용자 준비 필요)** — 사용자가 카카오 콘솔에 Redirect URI 2개를 등록하고 `backend/.env`에 `KAKAO_REST_API_KEY`·`KAKAO_CLIENT_SECRET`·`KAKAO_ADMIN_KEY`·`KAKAO_REDIRECT_URI=http://localhost:5173/api/auth/kakao/callback`을 넣은 뒤, `backend`(local 프로필)와 프론트를 **5173 포트 + `VITE_USE_MOCK=false`**로 띄워(Redirect URI가 5173이므로 `frontend-real-api`의 5174는 못 씀, 임시 실행 설정은 커밋하지 않음) 크롬에서:
  1. 새 카카오 계정 → 가입 마무리 → 대시보드, 새로고침해도 로그인 유지(Review Focus 1)
  2. 로그아웃 → 카카오 로그인 → 바로 로그인
  3. 카카오 동의 화면에서 취소 → 로그인 화면, 오류 문구 없음
  4. 마이페이지 탈퇴(`탈퇴` 입력) → 카카오 계정의 "연결된 서비스"에서 MSG가 사라졌는지 사용자에게 확인 요청
  5. 탈퇴 직후 다시 카카오 로그인 → 재가입 제한 문구(키가 켜져 있을 때)
  6. 모바일 크기·다크 모드에서 버튼 모양
- [ ] **Step 4: 커밋** `feat: 비밀번호 없는 계정의 탈퇴 화면` → **PR 2 (프론트)**. PR 미리보기에서는 카카오 키가 없어 버튼이 숨는 것까지 확인.

### Task 10: 개인정보 처리방침·약관 문구 (주미 검토)

**Files:**
- Modify: `frontend/src/pages/privacy/PrivacyPolicyPage.tsx`, `frontend/src/pages/terms/TermsPage.tsx` (실제 경로는 시작할 때 확인)

- [ ] **Step 1:** 처리방침 수집 항목에 "카카오 로그인을 쓰면: 카카오 회원번호, 카카오 계정 이메일, 닉네임(선택)" 추가, 수집 방법에 "카카오 로그인 시 카카오에서 제공받음", 파기에 "가입을 마치지 않으면 저장하지 않음(10분 뒤 브라우저 쿠키 만료)", 탈퇴 시 "카카오 연결 끊기"를 적는다. 재가입 제한 해시 보관이 카카오의 "탈퇴 시 파기" 의무와 맞는지 **주미 판단 대기** 표시.
- [ ] **Step 2:** `npm --prefix frontend run lint && npm --prefix frontend run build`
- [ ] **Step 3: 커밋** `docs: 처리방침에 카카오 로그인 수집 항목 추가` → **PR 3**, 주미 리뷰 요청.

---

## 범위 밖 (이번에 하지 않음)

- 카카오 "연결 끊기" 웹훅 받기(사용자가 카카오 쪽에서 먼저 끊을 때) — 콘솔 작업과 함께 다음에.
- 비밀번호 없는 사용자가 나중에 비밀번호 만들기, 기존 회원이 마이페이지에서 카카오 연결·해제.
- 구글 로그인, 소셜 전용 전환(주미와 결정 뒤).
