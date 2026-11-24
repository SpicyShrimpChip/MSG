# CLAUDE.md — backend

루트 `CLAUDE.md`의 원칙(미결 사항, 개인정보 처리, 점수는 코드로 계산)을 먼저 따른다.

## 스택

Spring Boot 3.x, Java 17, Gradle, Spring Web, Spring Data JPA, Spring Security, Validation,
Lombok, springdoc-openapi(Swagger)

## 명령어

```bash
./gradlew bootRun    # 로컬 실행 (http://localhost:8080)
./gradlew test       # 테스트 (H2)
./gradlew pgTest     # 같은 테스트를 PostgreSQL(msg_test)로
./gradlew build      # 빌드
```

Swagger UI: `/swagger-ui/index.html` — 프론트와 API를 맞출 때 이 문서를 기준으로 한다.

## 패키지 구조 (레퍼런스 프로젝트 방식)

```
<base-package>/
├── BackendApplication.java
├── domain/
│   └── <기능>/            auth, user, material, conversation, analysis,
│       ├── api/           quiz, curriculum, chatbot, notice
│       ├── application/   서비스(비즈니스 로직)
│       ├── dao/           JPA Repository
│       ├── dto/           요청·응답 DTO (Entity를 직접 응답하지 않는다)
│       └── entity/
└── global/
    ├── common/            ApiResponse 등 공통 응답
    ├── config/            CORS, Swagger, 웹 설정
    ├── error/             전역 예외 처리 (@RestControllerAdvice)
    ├── security/          Security 설정, JWT
    ├── llm/               LLM 클라이언트 인터페이스 (OpenAI·Anthropic·Gemini, 11/6 확정, 아래 참고)
    └── storage/           파일 저장 인터페이스 FileStorage (지금은 LocalFileStorage, S3는 구현 추가), 파일 삭제 작업(FileCleanupService)
```

## API 규칙

- **프론트가 기대하는 API·응답 형식·우선순위는 `docs/frontend-requests.md`에 있다. API를 만들기 전에 먼저 읽는다.**
- 경로: `/api/<기능>` (예: `/api/materials`, `/api/analysis/courses/{courseId}/runs`)
- 응답은 항상 `ApiResponse<T>`: `{ success, message, data }`
- 요청 DTO는 Bean Validation(`@Valid`, `@NotBlank` 등)으로 검증한다.
- 오류는 전역 예외 처리에서 일관된 형식과 적절한 HTTP 상태 코드로 반환하고, 스택트레이스나 내부 정보를 노출하지 않는다.

## 보안

- 인증 기본 방향: JWT access token + HttpOnly 쿠키 refresh token (레퍼런스 프로젝트 방식)
- 비밀번호는 BCrypt로 해시. 평문 저장·로그 출력 금지.
- Refresh Token은 갱신마다 회전하고, 폐기된 토큰이 다시 오면 세션 전체를 폐기한다(재사용 감지). 단 회전된 지 `app.auth.refresh-reuse-grace`(10초) 안이고 세션이 살아 있으면 탭 여러 개의 동시 갱신으로 보고 다시 발급한다(#211, `RefreshTokenService.isRotatedWithinGrace`). 토큰을 하나씩 폐기하는 기능(회전 말고)을 새로 만들면 이 판단이 깨지므로, 그때는 폐기 사유 칸을 추가한다.
- 모든 사용자 데이터 조회·수정은 로그인 사용자 소유인지 확인한다 (다른 사용자 id로 접근 차단). 과목·자료는 저장소 기본 메서드(`CourseRepository.getOwned`·`checkOwned`, `MaterialRepository.getOwned`, 없거나 남의 것이면 404)를, 사용자 행 잠금은 `UserRepository.lockLoginUser`(없으면 401)를 쓴다.
- DB 접속 정보, JWT 시크릿, LLM API 키, AWS 키는 `application.yaml`에 환경변수 참조로만 쓴다.
- 탈퇴 뒤 30일 재가입 제한(`RejoinRestriction`, `app.rejoin`, `withdrawn_account`, 11/21): 탈퇴 트랜잭션에서 정규화한 이메일의 HMAC-SHA256(키 `REJOIN_HASH_SECRET`)과 재가입 가능 시각만 남기고(이메일 원문·users 연결 없음), 가입할 때 기간 안이면 409, 기간이 지난 기록은 주기 작업이 지운다. 키가 없으면 기록도 확인도 하지 않는다. 기간·항목은 처리방침 2·4번과 같게 맞춘다.
- 카카오 로그인(11/24, `SocialAuthController`·`SocialLoginService`·`KakaoClient`, `app.oauth.kakao`·`app.frontend`): 브라우저가 `/api/auth/kakao/start` → 카카오 → `/api/auth/kakao/callback`으로 직접 이동하고, 끝나면 화면으로 302. state는 `msg_oauth_state_<state>` 쿠키(탭마다 따로, SameSite=Lax, 10분, 한 번만, #229)로 확인하고 맞지 않으면 카카오를 부르지 않는다. 돌아갈 경로는 `ReturnTo.sanitize`(사이트 안 경로만). 연결된 카카오 회원번호면 로그인, 카카오가 **확인한** 같은 이메일의 비밀번호 회원이면 **자동 연결하지 않고** 같은 서명 쿠키를 주고 연결 확인 화면으로 보내 그 계정의 비밀번호를 확인한 뒤(`POST /api/auth/social/link`, 로그인과 같은 시도 제한) 연결한다(비밀번호 가입은 이메일 주인을 확인하지 않아 남이 미리 만든 계정일 수 있다, 11/24 리뷰), 처음 온 사용자는 **DB에 쓰지 않고** 서명한 가입 마무리 쿠키(`msg_social_signup`, JWT aud=social-signup, 10분)만 주고 약관 동의(`POST /api/auth/social/signup`) 때 계정과 `user_social_account`(회원번호만)를 만든다(카카오 정책: 가입 전 저장 금지). 카카오로만 가입한 계정은 `password_hash`가 NULL이라 비밀번호 로그인은 없는 계정과 같게 실패하고, 탈퇴는 확인 문구 "탈퇴"로 한다. 탈퇴가 커밋되면 카카오 연결 끊기(unlink, 어드민 키)를 부르고 실패해도 탈퇴는 되돌리지 않는다(트랜잭션 콜백이 아니라 컨트롤러가 서비스 반환 뒤에 부른다: `afterCommit`은 DB 연결을 반납하기 전이다, #229). 콜백에서 가입을 거절할 때(이메일 없음·확인 안 된 이메일·같은 이메일 계정 충돌·재가입 제한)도 카카오 동의 순간 이미 생긴 연결을 끊는다(`SocialAuthController.handleOrUnlink`, 실패해도 안내는 같음). 가입 마무리 화면에서 그냥 나간 경우는 서버에 남는 것이 없어 끊지 않는다. 키(`KAKAO_*`)가 없으면 버튼 목록(`/api/auth/social/providers`)이 비고 시작 주소는 `socialError=unavailable`. 통합 테스트는 `TestKakao`·`FakeKakaoServer`로 돌아 실제 카카오를 부르지 않는다(`backend/.env`에 키가 있어도 덮는다).
- 회원가입은 Cloudflare Turnstile로 자동 가입(봇)을 막는다(`SignupBotCheck`, `app.turnstile`, 11/20). 비밀 키(`TURNSTILE_SECRET_KEY`)가 없으면 확인하지 않고(로컬·테스트), 운영 프로필은 `required: true`라 키가 없으면 서버가 뜨지 않는다(11/24). 확인 서버에 닿지 못하면 가입을 막는다(503). 외부 확인은 DB 트랜잭션 밖(컨트롤러)에서 먼저 한다. 같은 IP의 가입 시도는 `SignupAttemptLimiter`(`app.auth.signup-attempt`, 1시간 10번, 이메일 가입·카카오 가입 마무리 합계, 서버 메모리)로 제한한다. 통합 테스트는 모든 가입이 같은 IP라 이 제한을 끈다(`TestLlm`).
- CORS 허용 출처는 설정값으로 관리한다 (로컬: `http://localhost:5173`·`5174`, 운영: 환경변수 `CORS_ALLOWED_ORIGINS`).
- 업로드 파일은 확장자·크기와 **파일 내용**(실제 형식)을 검증한다(`MaterialFileInspector`). 브라우저가 보낸 MIME은 믿지 않고, 저장 위치(key)는 서버가 만들며 사용자가 보낸 파일 이름을 쓰지 않는다.
- 자료 올리기는 선택 `requestKey`(64자까지, `material.request_key`, UNIQUE `(user_id, request_key)`)를 받는다(11/24, #210). 같은 사용자·같은 키면 사용자 행을 잠근 채 이미 올린 자료를 찾아 200으로 돌려주고 파일을 다시 저장하지 않는다.
- DB 행과 저장소 파일을 함께 지울 때는 행 삭제 트랜잭션에서 `FileCleanupService.deleteAfterCommit`으로 삭제 작업을 남긴다(커밋 뒤 파일 삭제, 실패하면 주기 작업이 다시 시도). 파일을 먼저 지우지 않는다.

## DB 마이그레이션 (Flyway)

- 테이블 구조는 Flyway(`src/main/resources/db/migration`)로만 바꾼다. Hibernate는 `ddl-auto: validate`라 엔티티와 표가 맞는지 검사만 한다.
- **머지된 `V*.sql`은 고치지 않고 새 번호를 추가한다.** 지금은 `V1__init_schema.sql`·`V2__material_request_key.sql`(#210)·`V3__social_account.sql`(카카오 로그인)·`V4__user_ai_call_daily.sql`(사용자별 하루 AI 호출 몫, #234)·`V5__evidence_based_learning.sql`(인지 수준·자료 근거·요약본·학습 행동, feat/evidence-based-ai-learning)이고 다음은 `V6`다. 새 번호는 팀원과 겹치지 않게 미리 말한다. 11/24에 운영 DB를 처음 만들기 전 V1~V16을 V1 하나로 합쳤다(10/31에 이어 두 번째, 합치기 전 스키마와 `pg_dump --schema-only`로 같은 것을 확인). 운영 DB를 만든 뒤로는 **다시 합치지 않는다**(`docs/wiki/backend.md`). 코드 주석의 "V6 퀴즈"·"V11" 같은 표기는 합치기 전 버전 이력이다.
- 버전 번호는 `db/migration`과 `db/vector`가 **함께** 쓴다. 같은 번호를 두 폴더에 쓰지 않는다. pgvector 확장(`db/vector/V0_1`)은 H2에 없어 local·prod·`pgTest`에서만 읽고, 번호를 `0.1`로 둬서 항상 `V1`보다 먼저 돈다.
- 샘플 데이터는 `db/seed/R__*.sql`(반복 실행)이고 운영 프로필은 읽지 않는다.
- 앱 계정(`msg_app`)은 데이터 읽기·쓰기만 하고, 표 만들기·변경은 마이그레이션 계정(`msg_migrator`)만 한다.
- 로컬 DB는 도커(`db/docker-compose.yml`)로 켜고 `db/setup-local-postgres.sh`로 DB·계정을 만든다. 순서와 막힐 때 대처는 `docs/setup.md`. 실제 PostgreSQL 테스트는 `./gradlew pgTest`이고 이름이 `_test`로 끝나는 DB(`msg_test`)에서만 돈다.

## CI와 main 보호

- `main`은 삭제·강제 푸시가 막혀 있고, 프론트 CI(`build-and-lint`)는 머지 필수 검사다(10/27). 백엔드 CI(`build-and-test`, `postgres-test`)는 **아직 필수가 아니다**: 규칙셋 "backend CI required"는 만들어 두고 꺼 둔 상태이고, `backend-ci.yml`의 PR 실행은 `backend/**` 변경이 있을 때만 돈다.
- 백엔드 CI를 필수로 켤 때(백엔드 작업이 본격화되면 주미와 정하기): `backend-ci.yml`의 `pull_request`에서 `paths` 줄을 지우고, GitHub Settings → Rules → Rulesets에서 "backend CI required"를 Active로 바꾼다. 순서가 반대면 backend 밖만 바꾼 PR이 머지되지 않는다. 자세한 건 `backend-ci.yml` 위쪽 주석.
- 필수 검사 이름은 workflow의 job 이름(`build-and-test`, `postgres-test`)과 같아야 한다. job 이름을 바꾸면 규칙셋의 검사 이름도 같이 고친다.

## 미결 사항 처리 방법

- **LLM:** `global/llm`의 `LlmClient`를 제공사마다 구현했다(`OpenAiClient`·`AnthropicClient`·`GeminiClient`, 11/1). 키와 모델 이름은 환경변수(`OPENAI_API_KEY`·`OPENAI_MODEL`, `ANTHROPIC_API_KEY`·`ANTHROPIC_MODEL`, `GEMINI_API_KEY`·`GEMINI_MODEL`)로만 받고 기본 모델 이름은 두지 않는다. `GEMINI_API_KEY`는 결제가 연결된 프로젝트의 키여야 한다(무료 사용량은 입력이 제품 개선에 쓰여 개인정보 처리방침 9번과 어긋난다). **둘 중 하나라도 없으면 그 모델은 "미설정"으로 표시하고 부르지 않는다. 키가 없을 때 가짜 결과를 만들지 않는다.** 가짜 모델(`FakeLlmClients`)은 테스트 코드에만 있고, 통합 테스트는 키를 비워 실제 제공사를 부르지 않는다(`TestLlm`). **프롬프트 캐싱(11/5):** Claude는 `AnthropicClient`가 지시문(system) 끝에 `cache_control`을 붙이고, GPT·Gemini는 제공사가 자동으로 캐시한다. 그래서 지시문에는 요청마다 바뀌는 값(날짜·사용자·과목 등)을 넣지 않고 `user` 쪽에 둔다. 저장하는 입력 토큰 수는 세 제공사 모두 캐시 포함 입력 전체다.
- **학습 대화 분석 (`domain/analysis`):** 설정된 모델마다 같은 지시문(`AnalysisPrompt`, 바꾸면 `VERSION`을 올림)으로 따로 부르고, 다른 모델의 판단은 넣지 않는다. 모델 답변은 원본(`raw_output`)을 그대로 두고, `ModelOutputParser`가 개념 목록·상태·근거 메시지(보낸 메시지인지, 말한 사람이 맞는지)를 검증한다. 개념별 비교(`ConsensusCalculator`)는 정확도·숙련도가 아니다. 근거 부족을 이해 부족으로 바꾸지 않는다. 모델을 기다리는 동안 DB 트랜잭션을 잡지 않는다. 모델은 전체 시간 예산(`app.analysis.deadline` 100초. 배포에서 Vercel 프록시가 첫 응답을 120초까지만 기다리고 프론트 timeout은 115초라 그보다 짧게, 11/23) 안에서만 기다리고, 늦은 모델은 시간 초과(실패)로 두고 나머지 결과를 저장한다(11/21).
- **퀴즈 (`domain/quiz`, 11/3):** 학습 모드(시험 대비·자기개발·업무 활용)마다 지시문이 다르다(`QuizPrompt`, 바꾸면 `VERSION`을 올림). 만들기는 설정된 첫 모델 하나만 부르고(`app.quiz.generation-providers`), `QuizGenerationParser`를 통과한 퀴즈만 저장한다. **AI 퀴즈 처리 동의(`users.ai_quiz_consent`, 대화 분석 동의와 별도)가 있어야** 만들기·AI 채점을 한다. 비용 상한은 24시간 만들기 요청 수·AI 채점 실행 수(처음·다시 채점, `quiz_grading_run`)·실제 모델 요청 수(`quiz_ai_call` attempts 합) 세 겹이고, 한도 확인과 예약은 사용자 행을 잠근 같은 트랜잭션에서 한다. 만들기·채점은 전체 시간 예산(`generation-deadline`·`grading-deadline`) 안에서만 기다리고, 저장할 때 기록이 아직 진행 중·마감 전인지 확인해 늦은 결과는 버린다. 모델 호출은 `ModelInvoker`의 QUIZ 스레드 묶음(분석과 분리). 주관식 의미 판단·서술형 채점은 설정된 모델 모두가 따로 판단하고 과반 의견만 쓰며 점수는 코드가 더한다(`AnswerGradingService`). AI 채점을 못 하면 가짜 채점을 만들지 않고 `PENDING`으로 둔다. 외부로는 과목 이름·개념·시험 범위·직무 분야·학습자 답(사후평가는 사전평가 문제 글도)만 보내고 대화 원문은 보내지 않는다. 모델 호출 재시도는 공용 `global/llm/ModelInvoker`.
- **사용자별 24시간 횟수 (11/21, `user_ai_usage`, `global/llm/UserAiUsage`):** 분석 시작·퀴즈 만들기·AI 채점 실행 수는 실행 기록(`analysis_run` 등, 과목·제출에 CASCADE)이 아니라 `users`에만 연결한 이 기록으로 센다. 과목·대화·퀴즈를 지워도 한도가 다시 차지 않는다. 한도 확인과 `record`는 사용자 행을 잠근 같은 트랜잭션에서 하고, 48시간 지난 행은 주기 작업이 지운다. `quiz_ai_call`도 만들기·채점 기록이 지워질 때 연결만 끊고(SET NULL) 남는다. 새로 사용자별 횟수 한도를 만들면 여기에 종류를 추가한다. 예외로 자료 개념 뽑기·개념 설명·자료 요약(`MaterialConceptService`·`MaterialSummaryService`)은 `quiz_ai_call` 행(모델 시도마다 한 줄)으로 24시간 횟수를 센다. 실사용자 테스트 동안 비용을 보수적으로 세려고 이 방식을 유지한다(11/25).
- **하루 AI 호출 상한 (11/20 서비스 전체 `ai_daily_usage`, 11/24 사용자별 몫 `user_ai_call_daily`, `app.ai-daily-limit`):** 서비스 전체(`calls`)와 사용자 한 명의 몫(`user-calls`, 모든 기능의 호출 합계, 계정 몇 개로 전체 상한을 다 쓰지 못하게)을 하루(한국 시간 날짜) 단위로 센다. `ModelInvoker`가 부를 때마다(다시 부를 때도) 사용자 몫 → 서비스 전체 순서로 한 번씩 받아 가고, 다 찼으면 부르지 않고 `USER_DAILY_LIMIT`·`DAILY_LIMIT`을 돌려준다. 그래서 모델을 부르는 코드는 `invokeAsync(userId, ...)`에 사용자 id를 넘긴다. 분석·퀴즈 만들기·자료 개념 뽑기·개념 설명은 시작 전에 `ensureRemaining(userId, 부를 모델 수)`로 확인해 429를 준다(AI 채점은 `BUDGET_EXCEEDED` 대기로 남는다). `user_ai_call_daily`는 모델 스레드가 새 트랜잭션으로 쓰므로 `users`에 외래 키를 걸지 않고, 탈퇴 때 직접 지우며 지난 날짜는 주기 작업이 지운다. 서비스 전체 표(`ai_daily_usage`)는 사용자·과목과 연결하지 않아 과목 삭제·탈퇴·재가입으로 줄지 않는다. 새로 모델을 부르는 코드는 반드시 `ModelInvoker`를 거친다(직접 `LlmClient.complete`를 부르면 상한을 피해 간다).
- **사전·사후평가·자료 핵심 개념 (11/3):** 평가는 퀴즈 행(`assessment_kind`, `pre_assessment_id`)이고 사후평가는 최신 사전평가의 개념·개념 출처·유형별 수·난이도·모드를 그대로 쓴다(`QuizGenerationService`). 비교(`AssessmentService.compare`)는 최신 사전평가와 그 사후평가의 최근 제출에서 채점이 끝난 답만 세고, 변화량·개념 상태·비교 가능 조건을 코드로 계산한다. **학습자료 본문은 `app.material-content.enabled`(기본 false)가 켜져 있고 요청마다 확인(`materialContentConsent`)이 있을 때만 외부로 보낸다.** 처리 근거와 제공사·국가·보관 기간을 처리방침에 확정하기 전에는 켜지 않는다. 본문 읽기는 `MaterialTextExtractor`(TXT·MD·PPTX·PDFBox, 외부 서비스 없음, 스캔 PDF는 NO_TEXT), 핵심 개념은 `MaterialConceptService`가 AI 후보 중 이름·근거 문장이 본문에 실제로 있는 것만 `material_concept`에 저장한다(지어낸 개념은 버림). 자세한 계약은 `docs/frontend-requests.md` "11/3 사전·사후평가".
- **근거 기반 학습 (11/24, `docs/evidence-based-learning.md`):** 퀴즈는 문제마다 인지 수준(개정 블룸, `BloomPlan`, 난이도와 별개)·출제 목표를 받고, 자료 본문을 쓰면 `SourceText`가 개념이 나오는 쪽·슬라이드·문단만 골라 보내고 문제별 인용을 **보낸 글 안에서** 문자열로 대조한다(위치는 서버가 찾음, 근거 확인 문제가 절반 미만이면 저장 안 함). 요약본(`MaterialSummaryService`, `material_summary`)은 항목마다 원문 근거·AI 보충을 나누고 같은 파일 해시·지시문 버전이면 다시 쓴다. 대화 분석 v2는 근거마다 학습 행동을 받고 상태와 맞지 않으면 근거 부족으로 낮추며(재설명 요청·심화 질문만으로 이해 부족 확정 금지), 개념 검증 상태(`ConceptVerification`)의 "검증 완료"는 실제 확인 퀴즈가 같은 방향일 때만 준다. 인용 검증은 형식 검증이고 사실성 검증이 아니다.
- **AI가 하나뿐일 때:** 키와 모델 이름이 모두 있는 모델만 쓴다. 하나뿐이면 만들기·채점 모두 그 모델로 하고, 채점 판단이 하나면 다수결로 표시하지 않고 `SINGLE_MODEL`("추가 확인 필요")로 둔다. 하나도 없으면 503.
- **학습 피드백:** 사용자 의견(`concept_feedback`)은 AI 결과와 따로 두고 AI 판단·숙련도를 바꾸지 않는다. 추천 순서만 코드 규칙으로 바꾼다.
- **숙련도 점수 (산식 11/5 확정, 계산 클래스 구현함 `analysis/application/MasteryCalculator`, 설정 `app.mastery.*`, 분석 응답 `concepts[].mastery`, 기록 `MasteryHistoryService`·`concept_mastery`):** 최근 분석을 돌려줄 때(분석 실행·`runs/latest`) 개념의 마지막 기록과 단계·근거가 다르면 한 줄을 남기고 `mastery.history`(최근 30개)를 붙인다. 지난 분석(`runs/{id}`)은 기록하지 않고 붙이기만 한다. 모델 장애로 생긴 변화는 남기지 않는다(11/24, #209): 판단한 모델이 없는 분석(`failed`)은 기록하지 않고 `runs/latest`의 결과도 되지 않으며(그 시도는 `lastAttempt`로 알림), 일부 모델이 실패한 분석(`partial`)은 퀴즈·산식이 바뀌었거나 첫 기록일 때만 남긴다. 기록은 `analysis_run`에 CASCADE로 달려 대화 삭제·동의 철회 때 함께 지워진다. 대화 분류 결과(이해 못 함 / 재설명 요청 / 스스로 활용)와 퀴즈 정답률을 받아 점수를 계산하는 클래스를 분리하고, 가중치는 설정값으로 둔다. 초기값과 계산은 `docs/frontend-requests.md` 5-10절(퀴즈 1.0 : 대화 0.5, (Σw·s+1)÷(Σw+2), 근거 무게 합 3.0 미만은 "근거 부족"). LLM이 점수를 직접 매기지 않는다. 계산 로직에는 단위 테스트를 작성한다.
- **DB:** PostgreSQL 16 + pgvector(확정, MySQL에서 이주). 기본 프로필·`./gradlew test`는 H2(PostgreSQL 모드), 실제 DB 테스트는 `./gradlew pgTest`(`msg_test`). H2에서도 돌도록 마이그레이션에는 특정 DB 전용 문법을 쓰지 않고, `CREATE EXTENSION vector`처럼 PostgreSQL 전용인 것은 `db/vector/`에 둔다(local·prod에서만 적용). 로컬 DB는 `backend/db/docker-compose.yml` + `setup-local-postgres.sh`.
- **배치 JSON:** LLM이 출력한 화면 배치 JSON은 서버에서도 허용 컴포넌트 목록 기준으로 1차 검증한 뒤 프론트에 전달한다.

## 대화 가져오기 API

- 프론트에서 마스킹이 끝난 대화만 받는다. 그래도 서버에서 한 번 더 개인정보 패턴을 검사한다(`PersonalInfoDetector`, 규칙은 프론트 러스트 WASM `frontend/wasm/conversation-core/src/mask.rs`와 같게 유지).
- 요청 하나는 전부 저장하거나 전부 거절(422 + `data.rejected`)한다. 가져오기 작업(`conversation_import`)의 요청을 모두 받아야 완료이고, **분석은 `ConversationRepository.findAnalyzable`로 완료된 대화만 읽는다**(지금 동의한 사용자만).
- 대화 본문을 로그·예외 메시지에 남기지 않는다. 긴 글에 쓰는 정규식은 시작 위치마다 끝까지 읽지 않게 쓴다(`docs/wiki/backend.md`).
- 사용자가 AI 대화 분석 동의를 철회하면 가져온 대화와 그로부터 계산된 분석 결과를 함께 삭제한다(`UserService.changeAiAnalysisConsent` → `ConversationService.deleteAllOfUser` → `AnalysisCleanup`). 대화 한 건을 지우면 그 대화를 쓴 분석 전체를 지운다. 분석 결과 표를 새로 만들면 `analysis_run`에 외래 키(CASCADE)로 달거나 이 삭제에 추가한다.
