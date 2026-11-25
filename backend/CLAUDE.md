# CLAUDE.md — backend

루트 `CLAUDE.md`의 원칙(미결 사항, 개인정보 처리, 점수는 코드로 계산)을 먼저 따른다. 기능의 자세한 흐름은 `docs/backend-guide.md`(아래 "N절"은 이 문서의 절), 테이블·설계는 `docs/backend-design.md`.

## 스택

Spring Boot 3.x, Java 17, Gradle, Spring Web, Spring Data JPA, Spring Security, Validation, Lombok, springdoc-openapi(Swagger)

## 명령어

```bash
./gradlew bootRun    # 로컬 실행 (http://localhost:8080)
./gradlew test       # 테스트 (H2)
./gradlew pgTest     # 같은 테스트를 PostgreSQL(msg_test)로
./gradlew build      # 빌드
```

Swagger UI: `/swagger-ui/index.html` — 프론트와 API를 맞출 때 이 문서를 기준으로 한다.

## 패키지 구조

```
<base-package>/
├── domain/<기능>/      auth, user, material, conversation, analysis, quiz, curriculum, chatbot, notice
│   ├── api/ application/(서비스) dao/(JPA Repository) dto/ entity/
└── global/
    ├── common/ config/ error/(@RestControllerAdvice) security/(Security, JWT)
    ├── llm/            LLM 클라이언트 (OpenAI·Anthropic·Gemini)
    └── storage/        FileStorage(지금은 LocalFileStorage, S3는 구현 추가), FileCleanupService
```

**ArchUnit이 막는 규칙**(`src/test/java/com/msg/backend/architecture/`, 이유는 실패 메시지에): 컨트롤러가 Entity를 응답(제네릭 인자 포함), `LlmClient.complete` 직접 호출(→ `ModelInvoker`). 규칙을 늘리면 `ArchitectureRulesSelfTest`에 위반 예시(`com.msg.archfixture`, 스프링이 읽지 않는 패키지)도 같이 넣는다.

## API 규칙

- **프론트가 기대하는 API·응답 형식·우선순위는 `docs/frontend-requests.md`에 있다. API를 만들기 전에 먼저 읽는다.**
- 경로: `/api/<기능>` (예: `/api/materials`, `/api/analysis/courses/{courseId}/runs`). 응답은 항상 `ApiResponse<T>`: `{ success, message, data }`
- Entity를 직접 응답하지 않는다(도구가 막는다). 요청 DTO는 Bean Validation(`@Valid`, `@NotBlank` 등)으로 검증한다.
- 오류는 전역 예외 처리에서 일관된 형식과 적절한 HTTP 상태 코드로 반환하고, 스택트레이스나 내부 정보를 노출하지 않는다.

## 보안

- 인증: JWT access token + HttpOnly 쿠키 refresh token. 비밀번호는 BCrypt로 해시하고 평문 저장·로그 출력 금지.
- Refresh Token은 갱신마다 회전하고, 폐기된 토큰이 다시 오면 세션 전체를 폐기한다(재사용 감지, 동시 갱신 유예는 1절). 토큰을 하나씩 폐기하는 기능(회전 말고)을 새로 만들면 유예 판단이 깨지므로 폐기 사유 칸을 추가한다.
- 모든 사용자 데이터 조회·수정은 로그인 사용자 소유인지 확인한다. 과목·자료는 `CourseRepository.getOwned`·`checkOwned`, `MaterialRepository.getOwned`(없거나 남의 것이면 404), 사용자 행 잠금은 `UserRepository.lockLoginUser`(없으면 401)를 쓴다.
- DB 접속 정보, JWT 시크릿, LLM API 키, AWS 키는 `application.yaml`에 환경변수 참조로만 쓴다. CORS 허용 출처도 설정값(로컬 `http://localhost:5173`·`5174`, 운영 `CORS_ALLOWED_ORIGINS`).
- 탈퇴 뒤 재가입 제한(`RejoinRestriction`, 1절)은 이메일 원문을 남기지 않는다(HMAC만). 기간·항목은 처리방침 2·4번과 같게 맞춘다.
- 카카오 로그인(흐름은 1절): state 쿠키가 맞지 않으면 카카오를 부르지 않는다. 같은 이메일의 비밀번호 계정에 **자동 연결하지 않는다**(비밀번호 가입은 이메일 주인을 확인하지 않는다). 처음 온 사용자는 약관 동의 전 **DB에 쓰지 않는다**(카카오 정책). 가입을 거절할 때도 연결을 끊는다(동의 순간 이미 연결된다). 외부 API(unlink)는 `afterCommit`이 아니라 컨트롤러가 서비스 반환 뒤에 부른다(`afterCommit`은 DB 연결을 잡고 있다). 통합 테스트는 `TestKakao`·`FakeKakaoServer`로 돌아 실제 카카오를 부르지 않는다(`backend/.env`에 키가 있어도 덮는다).
- 가입 봇 확인(Turnstile, 1절): 외부 확인은 DB 트랜잭션 밖(컨트롤러)에서 먼저 한다. 통합 테스트는 모든 가입이 같은 IP라 가입 시도 제한을 끈다(`TestLlm`).
- 업로드 파일은 확장자·크기와 **파일 내용**(실제 형식)을 검증한다(`MaterialFileInspector`). 브라우저가 보낸 MIME은 믿지 않고, 저장 위치(key)는 서버가 만들며 사용자가 보낸 파일 이름을 쓰지 않는다.
- 자료 올리기의 `requestKey` 재전송·용량 상한은 사용자 행을 잠근 채 확인한다(2절, 동시 올리기로 넘기지 못하게). 파일을 저장하는 새 기능을 만들면 용량 합계에 넣는다.
- DB 행과 저장소 파일을 함께 지울 때는 행 삭제 트랜잭션에서 `FileCleanupService.deleteAfterCommit`으로 삭제 작업을 남긴다(커밋 뒤 삭제, 실패하면 주기 작업이 재시도). 파일을 먼저 지우지 않는다.

## DB 마이그레이션 (Flyway)

- 테이블 구조는 Flyway(`src/main/resources/db/migration`)로만 바꾼다. Hibernate는 `ddl-auto: validate`로 검사만 한다.
- **머지된 `V*.sql`은 고치지 않고 새 번호를 추가한다. 다음 번호는 `V6`.** 새 번호는 팀원과 겹치지 않게 미리 말한다. 운영 DB를 만든 뒤라 **다시 합치지 않는다**(`docs/wiki/backend.md`). 지금 목록과 합친 이력은 3절.
- 버전 번호는 `db/migration`과 `db/vector`가 **함께** 쓴다. 같은 번호를 두 폴더에 쓰지 않는다. pgvector 확장(`db/vector/V0_1`)은 H2에 없어 local·prod·`pgTest`에서만 읽고, `0.1`이라 항상 `V1`보다 먼저 돈다.
- H2에서도 돌도록 마이그레이션에 DB 전용 문법을 쓰지 않는다. `CREATE EXTENSION vector`처럼 PostgreSQL 전용은 `db/vector/`에 둔다.
- 샘플 데이터는 `db/seed/R__*.sql`(반복 실행)이고 운영 프로필은 읽지 않는다.
- 앱 계정(`msg_app`)은 데이터 읽기·쓰기만, 표 만들기·변경은 마이그레이션 계정(`msg_migrator`)만 한다.
- DB: PostgreSQL 16 + pgvector. 기본 프로필·`test`는 H2(PostgreSQL 모드). 로컬 DB는 `db/docker-compose.yml` + `db/setup-local-postgres.sh`(순서와 막힐 때는 `docs/setup.md`). `pgTest`는 이름이 `_test`로 끝나는 DB에서만 돈다.
- 테스트는 JVM 여러 개로 나눠 돈다(3절). 그래서 테스트끼리 static 값이나 서버 메모리 상태를 공유한다고 가정하지 않는다.

## CI와 main 보호

- `main`은 삭제·강제 푸시가 막혀 있고, 프론트 CI(`build-and-lint`)는 머지 필수 검사다. 백엔드 CI(`build-and-test`, `postgres-test`)는 **아직 필수가 아니다**: 규칙셋 "backend CI required"는 꺼 둔 상태이고, `backend-ci.yml`의 PR 실행은 `backend/**` 변경이 있을 때만 돈다.
- 백엔드 CI를 필수로 켤 때(주미와 정하기): `backend-ci.yml`의 `pull_request`에서 `paths` 줄을 먼저 지우고, 그다음 GitHub Rulesets에서 "backend CI required"를 Active로 바꾼다. 순서가 반대면 backend 밖만 바꾼 PR이 머지되지 않는다(`backend-ci.yml` 위쪽 주석).
- 필수 검사 이름은 workflow의 job 이름과 같아야 한다. job 이름을 바꾸면 규칙셋의 검사 이름도 같이 고친다.

## LLM·분석·퀴즈 규칙

- **LLM 키:** `LlmClient`를 제공사마다 구현했다(`OpenAiClient`·`AnthropicClient`·`GeminiClient`). 키와 모델 이름은 환경변수(`OPENAI_API_KEY`·`OPENAI_MODEL`, `ANTHROPIC_*`, `GEMINI_*`)로만 받고 기본 모델 이름은 두지 않는다. `GEMINI_API_KEY`는 결제가 연결된 프로젝트의 키여야 한다(무료 사용량은 입력이 제품 개선에 쓰여 처리방침 9번과 어긋난다). **둘 중 하나라도 없으면 그 모델은 "미설정"으로 표시하고 부르지 않는다. 키가 없을 때 가짜 결과를 만들지 않는다.** 가짜 모델(`FakeLlmClients`)은 테스트 코드에만 두고, 통합 테스트는 키를 비워 실제 제공사를 부르지 않는다(`TestLlm`).
- **지시문에는 요청마다 바뀌는 값(날짜·사용자·과목 등)을 넣지 않고 `user` 쪽에 둔다**(프롬프트 캐시가 깨진다, 4절). 지시문(`AnalysisPrompt`·`QuizPrompt`)을 바꾸면 `VERSION`을 올린다.
- **모델 호출은 반드시 `ModelInvoker`를 거치고 `invokeAsync(userId, ...)`에 사용자 id를 넘긴다**(하루 상한을 피해 가지 않게, 도구가 막는다). 재시도도 `ModelInvoker`. 상한 표의 오늘 줄 만들기는 `Persistable`로 persist한다(`save`/merge면 동시에 올린 수를 0으로 덮어쓴다, `docs/wiki/backend.md`). 상한 동작은 4절.
- 사용자별 횟수 한도를 새로 만들면 `user_ai_usage`(`UserAiUsage`)에 종류를 추가한다. 한도 확인과 기록·예약은 사용자 행을 잠근 같은 트랜잭션에서 한다(4절).
- **대화 분석(`domain/analysis`):** 설정된 모델마다 같은 지시문으로 따로 부르고, 다른 모델의 판단은 넣지 않는다. 답변 원본(`raw_output`)은 그대로 두고 `ModelOutputParser`가 검증한다. 개념별 비교(`ConsensusCalculator`)는 정확도·숙련도가 아니다. 근거 부족을 이해 부족으로 바꾸지 않는다(재설명 요청·심화 질문만으로 이해 부족 확정 금지). 모델을 기다리는 동안 DB 트랜잭션을 잡지 않는다. 시간 예산(`app.analysis.deadline`)은 Vercel 프록시 한도(120초)보다 짧게 둔다(4절).
- **퀴즈(`domain/quiz`, 5절):** **AI 퀴즈 처리 동의(`users.ai_quiz_consent`, 대화 분석 동의와 별도)가 있어야** 만들기·AI 채점을 한다. AI 채점을 못 하면 가짜 채점을 만들지 않고 `PENDING`으로 둔다. 대화 원문은 외부로 보내지 않는다.
- **AI가 하나뿐일 때:** 키와 모델 이름이 모두 있는 모델만 쓴다. 하나뿐이면 만들기·채점 모두 그 모델로 하고, 채점 판단이 하나면 다수결로 표시하지 않고 `SINGLE_MODEL`("추가 확인 필요")로 둔다. 하나도 없으면 503.
- **학습자료 본문은 `app.material-content.enabled`(기본 false)가 켜져 있고 요청마다 확인(`materialContentConsent`)이 있을 때만 외부로 보낸다.** 처리 근거와 제공사·국가·보관 기간을 처리방침에 확정하기 전에는 켜지 않는다(6절).
- 자료 개념·인용은 본문에 실제로 있는 것만 저장한다(지어낸 개념 버림, 6·7절). 인용 검증은 형식 검증이고 사실성 검증이 아니다. "검증 완료"는 실제 확인 퀴즈가 같은 방향일 때만 준다.
- **학습 피드백:** 사용자 의견(`concept_feedback`)은 AI 결과와 따로 두고 AI 판단·숙련도를 바꾸지 않는다. 추천 순서만 코드 규칙으로 바꾼다.
- **숙련도 점수(`MasteryCalculator`, `app.mastery.*`, 8절):** LLM이 점수를 직접 매기지 않는다. 가중치는 설정값으로 두고(산식은 `docs/frontend-requests.md` 5-10절), 계산 로직에는 단위 테스트를 쓴다. 모델 장애로 생긴 변화는 기록하지 않는다(모델이 모두 실패한 분석은 최근 결과가 되지 않는다).
- **배치 JSON:** LLM이 출력한 화면 배치 JSON은 서버에서도 허용 컴포넌트 목록 기준으로 1차 검증한 뒤 프론트에 전달한다.

## 대화 가져오기 API

- 프론트에서 마스킹이 끝난 대화만 받고, 서버에서 한 번 더 개인정보 패턴을 검사한다(`PersonalInfoDetector`, 규칙은 프론트 `frontend/wasm/conversation-core/src/mask.rs`와 같게 유지).
- 요청 하나는 전부 저장하거나 전부 거절(422 + `data.rejected`)한다. 가져오기 작업(`conversation_import`)의 요청을 모두 받아야 완료이고, **분석은 `ConversationRepository.findAnalyzableInCourse`로 완료된 대화만 읽는다**(지금 동의한 사용자만).
- 분석에 보낼 메시지는 과목 키워드로 고른다(`AnalysisInputBuilder`). **키워드 찾기 `global/common/TermMatch`(영문은 낱말 경계, 한글이 들면 부분 문자열)는 프론트 `filter.ts`와 같은 규칙이다. 한쪽을 바꾸면 다른 쪽과 양쪽 테스트 표를 같이 고친다**(다르면 화면에서 고른 대화가 분석에서 빠진다).
- 대화 본문을 로그·예외 메시지에 남기지 않는다. 긴 글에 쓰는 정규식은 시작 위치마다 끝까지 읽지 않게 쓴다(`docs/wiki/backend.md`).
- AI 대화 분석 동의를 철회하면 가져온 대화와 분석 결과를 함께 삭제한다(`UserService.changeAiAnalysisConsent` → `ConversationService.deleteAllOfUser` → `AnalysisCleanup`). 대화 한 건을 지우면 그 대화를 쓴 분석 전체를 지운다. **분석 결과 표를 새로 만들면 `analysis_run`에 외래 키(CASCADE)로 달거나 이 삭제에 추가한다.**
