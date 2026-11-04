# CLAUDE.md — backend

루트 `CLAUDE.md`의 원칙(미결 사항, 개인정보 처리, 점수는 코드로 계산)을 먼저 따른다.

## 스택

Spring Boot 3.x, Java 17, Gradle, Spring Web, Spring Data JPA, Spring Security, Validation,
Lombok, springdoc-openapi(Swagger)

## 명령어

```bash
./gradlew bootRun    # 로컬 실행 (http://localhost:8080)
./gradlew test       # 테스트 (H2)
./gradlew pgTest     # 같은 테스트를 PostgreSQL(ai_lms_test)로
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
    ├── llm/               LLM 클라이언트 인터페이스 (제공사 미정, 아래 참고)
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
- 모든 사용자 데이터 조회·수정은 로그인 사용자 소유인지 확인한다 (다른 사용자 id로 접근 차단).
- DB 접속 정보, JWT 시크릿, LLM API 키, AWS 키는 `application.yaml`에 환경변수 참조로만 쓴다.
- CORS 허용 출처는 설정값으로 관리한다 (로컬: `http://localhost:5173`·`5174`, 운영: 환경변수 `CORS_ALLOWED_ORIGINS`).
- 업로드 파일은 확장자·크기와 **파일 내용**(실제 형식)을 검증한다(`MaterialFileInspector`). 브라우저가 보낸 MIME은 믿지 않고, 저장 위치(key)는 서버가 만들며 사용자가 보낸 파일 이름을 쓰지 않는다.
- DB 행과 저장소 파일을 함께 지울 때는 행 삭제 트랜잭션에서 `FileCleanupService.deleteAfterCommit`으로 삭제 작업을 남긴다(커밋 뒤 파일 삭제, 실패하면 주기 작업이 다시 시도). 파일을 먼저 지우지 않는다.

## DB 마이그레이션 (Flyway)

- 테이블 구조는 Flyway(`src/main/resources/db/migration`)로만 바꾼다. Hibernate는 `ddl-auto: validate`라 엔티티와 표가 맞는지 검사만 한다.
- **머지된 `V*.sql`은 고치지 않고 새 번호를 추가한다.** 지금은 `V1__init_schema.sql`, `V2__create_course.sql`, `V3__create_material.sql`, `V4__create_conversation.sql`, `V5__create_learning_analysis.sql`, `V6__create_quiz.sql`, `V7__add_learning_purpose_and_quiz_modes.sql`, `V8__add_quiz_ai_consent_and_call_tracking.sql`, `V9__add_pre_post_assessment.sql`, `V10__create_material_concept.sql`, `V11__add_material_concept_study_note.sql`, `V12__create_concept_study_progress.sql`이고 다음은 `V13`이다. 새 번호는 팀원과 겹치지 않게 미리 말한다. 10/31에 `V1`부터 다시 쓴 것은 운영 DB가 없어서 가능했던 예외이고, 운영 DB가 생기면 하지 않는다(`docs/wiki/backend.md`).
- 버전 번호는 `db/migration`과 `db/vector`가 **함께** 쓴다. 같은 번호를 두 폴더에 쓰지 않는다. pgvector 확장(`db/vector/V0_1`)은 H2에 없어 local·prod·`pgTest`에서만 읽고, 번호를 `0.1`로 둬서 항상 `V1`보다 먼저 돈다.
- 샘플 데이터는 `db/seed/R__*.sql`(반복 실행)이고 운영 프로필은 읽지 않는다.
- 앱 계정(`ai_lms_app`)은 데이터 읽기·쓰기만 하고, 표 만들기·변경은 마이그레이션 계정(`ai_lms_migrator`)만 한다.
- 로컬 DB는 도커(`db/docker-compose.yml`)로 켜고 `db/setup-local-postgres.sh`로 DB·계정을 만든다. 순서와 막힐 때 대처는 `docs/setup.md`. 실제 PostgreSQL 테스트는 `./gradlew pgTest`이고 이름이 `_test`로 끝나는 DB(`ai_lms_test`)에서만 돈다.

## CI와 main 보호

- `main`은 삭제·강제 푸시가 막혀 있고, 프론트 CI(`build-and-lint`)는 머지 필수 검사다(10/27). 백엔드 CI(`build-and-test`, `postgres-test`)는 **아직 필수가 아니다**: 규칙셋 "backend CI required"는 만들어 두고 꺼 둔 상태이고, `backend-ci.yml`의 PR 실행은 `backend/**` 변경이 있을 때만 돈다.
- 백엔드 CI를 필수로 켤 때(백엔드 작업이 본격화되면 주미와 정하기): `backend-ci.yml`의 `pull_request`에서 `paths` 줄을 지우고, GitHub Settings → Rules → Rulesets에서 "backend CI required"를 Active로 바꾼다. 순서가 반대면 backend 밖만 바꾼 PR이 머지되지 않는다. 자세한 건 `backend-ci.yml` 위쪽 주석.
- 필수 검사 이름은 workflow의 job 이름(`build-and-test`, `postgres-test`)과 같아야 한다. job 이름을 바꾸면 규칙셋의 검사 이름도 같이 고친다.

## 미결 사항 처리 방법

- **LLM:** `global/llm`의 `LlmClient`를 제공사마다 구현했다(`OpenAiClient`·`AnthropicClient`·`GeminiClient`, 11/1). 키와 모델 이름은 환경변수(`OPENAI_API_KEY`·`OPENAI_MODEL`, `ANTHROPIC_API_KEY`·`ANTHROPIC_MODEL`, `GEMINI_API_KEY`·`GEMINI_MODEL`)로만 받고 기본 모델 이름은 두지 않는다. **둘 중 하나라도 없으면 그 모델은 "미설정"으로 표시하고 부르지 않는다. 키가 없을 때 가짜 결과를 만들지 않는다.** 가짜 모델(`FakeLlmClients`)은 테스트 코드에만 있고, 통합 테스트는 키를 비워 실제 제공사를 부르지 않는다(`TestLlm`).
- **학습 대화 분석 (`domain/analysis`):** 설정된 모델마다 같은 지시문(`AnalysisPrompt`, 바꾸면 `VERSION`을 올림)으로 따로 부르고, 다른 모델의 판단은 넣지 않는다. 모델 답변은 원본(`raw_output`)을 그대로 두고, `ModelOutputParser`가 개념 목록·상태·근거 메시지(보낸 메시지인지, 말한 사람이 맞는지)를 검증한다. 개념별 비교(`ConsensusCalculator`)는 정확도·숙련도가 아니다. 근거 부족을 이해 부족으로 바꾸지 않는다. 모델을 기다리는 동안 DB 트랜잭션을 잡지 않는다.
- **퀴즈 (`domain/quiz`, 11/3):** 학습 모드(시험 대비·자기개발·업무 활용)마다 지시문이 다르다(`QuizPrompt`, 바꾸면 `VERSION`을 올림). 만들기는 설정된 첫 모델 하나만 부르고(`app.quiz.generation-providers`), `QuizGenerationParser`를 통과한 퀴즈만 저장한다. **AI 퀴즈 처리 동의(`users.ai_quiz_consent`, 대화 분석 동의와 별도)가 있어야** 만들기·AI 채점을 한다. 비용 상한은 24시간 만들기 요청 수·AI 채점 실행 수(처음·다시 채점, `quiz_grading_run`)·실제 모델 요청 수(`quiz_ai_call` attempts 합) 세 겹이고, 한도 확인과 예약은 사용자 행을 잠근 같은 트랜잭션에서 한다. 만들기·채점은 전체 시간 예산(`generation-deadline`·`grading-deadline`) 안에서만 기다리고, 저장할 때 기록이 아직 진행 중·마감 전인지 확인해 늦은 결과는 버린다. 모델 호출은 `ModelInvoker`의 QUIZ 스레드 묶음(분석과 분리). 주관식 의미 판단·서술형 채점은 설정된 모델 모두가 따로 판단하고 과반 의견만 쓰며 점수는 코드가 더한다(`AnswerGradingService`). AI 채점을 못 하면 가짜 채점을 만들지 않고 `PENDING`으로 둔다. 외부로는 과목 이름·개념·시험 범위·직무 분야·학습자 답(사후평가는 사전평가 문제 글도)만 보내고 대화 원문은 보내지 않는다. 모델 호출 재시도는 공용 `global/llm/ModelInvoker`.
- **사전·사후평가·자료 핵심 개념 (11/3, V9·V10):** 평가는 퀴즈 행(`assessment_kind`, `pre_assessment_id`)이고 사후평가는 최신 사전평가의 개념·개념 출처·유형별 수·난이도·모드를 그대로 쓴다(`QuizGenerationService`). 비교(`AssessmentService.compare`)는 최신 사전평가와 그 사후평가의 최근 제출에서 채점이 끝난 답만 세고, 변화량·개념 상태·비교 가능 조건을 코드로 계산한다. **학습자료 본문은 `app.material-content.enabled`(기본 false)가 켜져 있고 요청마다 확인(`materialContentConsent`)이 있을 때만 외부로 보낸다.** 처리 근거와 제공사·국가·보관 기간을 처리방침에 확정하기 전에는 켜지 않는다. 본문 읽기는 `MaterialTextExtractor`(TXT·MD·PPTX·PDFBox, 외부 서비스 없음, 스캔 PDF는 NO_TEXT), 핵심 개념은 `MaterialConceptService`가 AI 후보 중 이름·근거 문장이 본문에 실제로 있는 것만 `material_concept`에 저장한다(지어낸 개념은 버림). 자세한 계약은 `docs/frontend-requests.md` "11/3 사전·사후평가".
- **AI가 하나뿐일 때:** 키와 모델 이름이 모두 있는 모델만 쓴다. 하나뿐이면 만들기·채점 모두 그 모델로 하고, 채점 판단이 하나면 다수결로 표시하지 않고 `SINGLE_MODEL`("추가 확인 필요")로 둔다. 하나도 없으면 503.
- **학습 피드백:** 사용자 의견(`concept_feedback`)은 AI 결과와 따로 두고 AI 판단·숙련도를 바꾸지 않는다. 추천 순서만 코드 규칙으로 바꾼다.
- **숙련도 점수:** 대화 분류 결과(이해 못 함 / 재설명 요청 / 스스로 활용)와 퀴즈 정답률을 받아 점수를 계산하는 클래스를 분리하고, 가중치는 설정값으로 둔다. LLM이 점수를 직접 매기지 않는다. 계산 로직에는 단위 테스트를 작성한다.
- **DB:** PostgreSQL 16 + pgvector(확정, MySQL에서 이주). 기본 프로필·`./gradlew test`는 H2(PostgreSQL 모드), 실제 DB 테스트는 `./gradlew pgTest`(`ai_lms_test`). H2에서도 돌도록 마이그레이션에는 특정 DB 전용 문법을 쓰지 않고, `CREATE EXTENSION vector`처럼 PostgreSQL 전용인 것은 `db/vector/`에 둔다(local·prod에서만 적용). 로컬 DB는 `backend/db/docker-compose.yml` + `setup-local-postgres.sh`.
- **배치 JSON:** LLM이 출력한 화면 배치 JSON은 서버에서도 허용 컴포넌트 목록 기준으로 1차 검증한 뒤 프론트에 전달한다.

## 대화 가져오기 API

- 프론트에서 마스킹이 끝난 대화만 받는다. 그래도 서버에서 한 번 더 개인정보 패턴을 검사한다(`PersonalInfoDetector`, 규칙은 프론트 `mask.ts`와 같게 유지).
- 요청 하나는 전부 저장하거나 전부 거절(422 + `data.rejected`)한다. 가져오기 작업(`conversation_import`)의 요청을 모두 받아야 완료이고, **분석은 `ConversationRepository.findAnalyzable`로 완료된 대화만 읽는다**(지금 동의한 사용자만).
- 대화 본문을 로그·예외 메시지에 남기지 않는다. 긴 글에 쓰는 정규식은 시작 위치마다 끝까지 읽지 않게 쓴다(`docs/wiki/backend.md`).
- 사용자가 AI 대화 분석 동의를 철회하면 가져온 대화와 그로부터 계산된 분석 결과를 함께 삭제한다(`UserService.changeAiAnalysisConsent` → `ConversationService.deleteAllOfUser` → `AnalysisCleanup`). 대화 한 건을 지우면 그 대화를 쓴 분석 전체를 지운다. 분석 결과 표를 새로 만들면 `analysis_run`에 외래 키(CASCADE)로 달거나 이 삭제에 추가한다.
