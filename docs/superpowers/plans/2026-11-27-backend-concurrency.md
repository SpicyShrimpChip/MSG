# 백엔드 동시성·작은 버그 고치기 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 코드 검토에서 확인된 백엔드 동시성 문제 3건(늦게 끝난 채점 실행, 대화 삭제와 분석 시작, 제출 중 동의 철회)과 작은 버그 4건(서술형 피드백 모델, 전체 상한에 막힌 호출의 사용자 몫, 탈퇴 때 빈 저장 키, 파일 정리 재시도 순서)을 PR 두 개로 고친다.

**Architecture:** 동시성 3건은 모두 "사용자 행 잠금(`UserRepository.lockLoginUser`) 안에서 다시 확인한다"는 기존 규칙을 빠진 곳에 맞추는 수정이고, DB 구조는 바꾸지 않는다. 채점 실행은 답에 실행 id를 새로 저장하지 않고 "실행이 아직 RUNNING일 때만 답을 건드린다"로 막는다(근거는 Task 1). 작은 버그 4건은 각 클래스 안의 수정이고, 파일 정리 재시도는 이미 있는 `attempt_count`·`last_attempted_at` 열로 순서를 바꾸므로 마이그레이션이 없다.

**Tech Stack:** Spring Boot 3.5 / Java 17, Spring Data JPA, Flyway(이번엔 새 파일 없음), JUnit 5 + MockMvc 통합 테스트(`@IntegrationTest`, H2와 PostgreSQL 둘 다).

**Spec:** 별도 설계 문서 없음. 이 계획의 "지적과 판단" 표가 검토 결과 원문과 판단이다. 규칙 근거는 루트 `CLAUDE.md`, `backend/CLAUDE.md`("보안", "DB 마이그레이션", "LLM·분석·퀴즈 규칙", "대화 가져오기 API"), `docs/backend-guide.md` 4·5절, `docs/wiki/backend.md`.

### 지적과 판단

| # | 지적 | 판단 | Task |
|---|---|---|---|
| 1 | `QuizService.executeRun`: 마감을 넘긴 실행 A가 그사이 regrade로 시작된 실행 B의 GRADING 답을 `markGradingFailed`로 망가뜨리고, B는 결과를 버린 채 SUCCEEDED | 사실. 마이그레이션 없이 고친다 | 1 |
| 2 | `ConversationService.delete`: 사용자 행 잠금 없이 분석 정리 → 커밋 전 분석 실행이 안 보임 | 사실(PostgreSQL에서는 외래 키 잠금 때문에 대화 삭제가 분석 커밋을 기다린 뒤 `analysis_run_conversation`만 지우고 `analysis_run`은 남는다) | 3 |
| 3 | `QuizService.submit`: 동의 확인을 잠금 밖에서만 | 사실. `regrade`는 잠근 뒤 확인 | 2 |
| 4 | `AnswerGradingService.essay`: 피드백을 늘 `judged.get(0)`에서 | 사실 | 4 |
| 5 | `MaterialConceptController.extract`: `@Valid` 없음, 본문 `null`이면 500 | **사실 아님 → 계획에서 뺐다.** `@RequestBody`는 기본 `required=true`라 본문 `null`이면 Spring이 `HttpMessageNotReadableException`을 던지고 `GlobalExceptionHandler.handleUnreadable`이 400을 준다. `ConceptExtractRequest`에는 검증 애너테이션이 없어 `@Valid`를 붙여도 동작이 같다(확인 없음은 서비스가 400) | — |
| 6 | `ModelInvoker.invoke`: 사용자 몫을 올린 뒤 전체 상한에 막히면 부르지도 않고 몫만 줄어듦 | 사실. 순서는 그대로 두고(몫을 다 쓴 사용자가 전체 상한을 줄이지 않게, `ModelInvokerDeadlineTest`가 지킨다) 받아 간 몫을 돌려준다 | 5 |
| 7 | `WithdrawalService.withdraw`: 저장 키 null을 거르지 않음 | 사실(`material.storage_key`는 NULL 허용, `file_deletion_task.storage_key`는 NOT NULL이라 null이 오면 탈퇴 트랜잭션이 실패한다). 지금은 생기지 않는 잠재 버그 | 6 |
| 8 | `FileCleanupService.retryPending`: 늘 `findTop100ByOrderByIdAsc` | 사실. 열(`attempt_count`·`last_attempted_at`)은 V1에 이미 있어 스키마 변경 없이 고친다 | 7 |

**범위 밖:** 큰 PDF 추출 동안 사용자 잠금을 오래 쥐는 문제(`MaterialConceptService`·`MaterialSummaryService.prepare`, 중복 정리 계획), 숙련도 계산, 로그인 제한.

## Global Constraints

- **PR 두 개, main 756f6f7에서 각각 브랜치를 딴다.**
  - PR 1 `fix/backend-concurrency`: Task 1·2·3 (+ 위키 교훈 한 건). 커밋 예: `fix: 늦게 끝난 채점 실행이 다시 채점 중인 답을 건드리지 않게`.
  - PR 2 `fix/backend-small-bugs`: Task 4·5·6·7. 커밋 예: `fix: 서술형 피드백을 과반 의견 모델에서 가져오기`.
  - 두 PR은 서로 같은 파일을 고치지 않는다(PR 1의 `QuizSafetyApiTest`는 PR 2의 Task 4도 고치므로 **PR 1을 먼저 머지**하고 PR 2를 그 위로 올린다. 순서가 바뀌면 테스트 파일 충돌만 난다).
- 커밋 형식: `fix: <한국어 설명>`. Task마다 커밋한다(화면 피드백 작업이 아니다). 푸시·PR 만들기는 사용자가 요청할 때만 한다.
- API 응답·요청 형식, DTO, DB 마이그레이션 변경 없음(다음 번호 `V6`은 쓰지 않는다). 그래서 프론트 타입·`docs/frontend-requests.md`는 건드리지 않는다.
- 주석·문서·테스트 이름은 한국어, 식별자는 영어. 테스트 메서드 이름은 이 레포처럼 한국어 문장(`void 늦게_끝난_..._()`).
- 명령은 모두 `cd backend`에서:
  - 한 클래스: `./gradlew test --tests "*QuizSafetyApiTest"` (H2)
  - 같은 테스트를 PostgreSQL로: `./gradlew pgTest --tests "*QuizSafetyApiTest"` (이름이 `_test`로 끝나는 로컬 DB 필요, 준비는 `docs/setup.md`)
  - PR 끝: `./gradlew build`(H2 테스트 + ArchUnit 포함) **와** `./gradlew pgTest` 둘 다 통과.
- **동시성 테스트는 H2와 PostgreSQL 둘 다에서 돌리고, 판정은 PostgreSQL(`pgTest`, CI `postgres-test`)로 한다.** 위키 교훈: DB마다 다른 동작은 H2 테스트로 잡히지 않는다(NUL 문자 건), 가끔 실패하는 동시성 테스트는 다시 돌리기 전에 운영 코드의 경쟁부터 의심한다(하루 호출 수 merge 건). 이번 경쟁은 PostgreSQL의 외래 키 잠금(`FOR KEY SHARE`)·MVCC 읽기에 기대므로 H2만 통과해서는 고쳤다고 보지 않는다.
- 동시성 테스트에서 다른 스레드가 잠금을 쥐는 시간은 300~500ms로 짧게 두고(H2 잠금 대기 한도 안), 스레드 풀은 `finally`에서 `shutdownNow()`, 결과는 `Future.get(30, SECONDS)`로 기다린다(`MaterialApiTest`의 동시 올리기 테스트 형식).
- 테스트는 JVM 여러 개로 나눠 돈다. static 값·서버 메모리 상태를 테스트끼리 공유한다고 가정하지 않는다(`backend/CLAUDE.md`).
- 대화 본문·학습자 답을 로그·예외 메시지에 남기지 않는다.
- 문서에 덧붙이기 전에 고칠 문단을 먼저 찾는다. 경과(날짜·PR 번호)는 문서에 쓰지 않는다(위키 교훈 한 건만 예외).

## Review Focus

- **늦은 실행 A를 정리한 뒤 실제 실행 B가 끝나는 경우:** B가 자기 결과를 답에 반영하고 SUCCEEDED가 되어야 한다. Task 1 테스트는 B를 DB 행으로 흉내 내므로 B의 반영은 "B의 마감이 지난 뒤 다시 채점하면 정상 채점된다"로 이어서 확인한다. → Task 1 Step 1 후반부.
- **서버가 재시작돼 RUNNING으로 남은 채 마감이 지난 실행이 스스로 끝나는 경우**(아무도 정리하지 않음): 지금처럼 TIMED_OUT + 답 MODEL_FAILED여야 한다. "RUNNING이 아니면 건드리지 않는다"를 "마감이 지나면 건드리지 않는다"로 잘못 쓰면 깨진다. → 기존 `마감이_지난_뒤_도착한_채점은_반영하지_않고_다시_채점할_수_있게_둔다`가 지킨다(Task 1 Step 4에서 함께 돌린다).
- **제출 도중 동의를 철회한 사용자:** 철회가 잠금 대기 중에 커밋되면 모델을 부르지 않고 NO_CONSENT 대기로 남아야 한다(가짜 채점 금지). → Task 2 테스트.
- **날짜가 바뀌는 순간 전체 상한에 막힌 호출:** 돌려주기가 0 아래로 내려가면 안 된다(`call_count > 0`일 때만 줄인다). → Task 5 테스트 `줄이_0이면_돌려줘도_0이다`.
- **계속 실패하는 파일 정리 작업이 100건 넘게 쌓인 경우:** 뒤에 있는 지울 수 있는 작업도 다음 정리 때 처리돼야 한다. → Task 7 테스트.

---

## PR 1 `fix/backend-concurrency`

### Task 1: 끝난 채점 실행은 답을 건드리지 않는다

**문제:** `QuizService.executeRun`(269행, 반영 트랜잭션 280-306행)은 실행 상태와 상관없이 `answer.isBeingGraded()`인 답을 모두 처리한다. 마감을 넘긴 A가 반영할 때, 그사이 `regrade`가 `expireStaleRuns`로 A를 TIMED_OUT으로 끝내고 B를 예약해 같은 답을 다시 GRADING으로 표시했으면, A가 B의 답을 MODEL_FAILED로 바꾼다. 그 뒤 B는 GRADING 답이 없어 결과를 버리고, `results`만 보고 SUCCEEDED로 끝난다.

**마이그레이션을 하지 않는 근거:** 한 제출의 GRADING 답을 맡은 실행은 언제나 하나다. 사용자 행 잠금 안에서 `regrade`는 `expireStaleRuns`로 마감 지난 RUNNING 실행을 먼저 끝내고(그 실행의 GRADING 답도 같은 트랜잭션에서 MODEL_FAILED로 바꿈), 마감 전 RUNNING 실행이 있으면 409로 막으며, INITIAL 실행은 새 제출에만 생긴다. 그래서 "반영 시점에 실행이 아직 RUNNING이면 GRADING 답은 그 실행의 것"이고, RUNNING이 아니면 그 답은 이미 정리됐거나 다른 실행의 것이다. 답에 `grading_run_id`를 두는 것(V6)은 이 불변식이 깨질 새 경로가 생길 때 다시 본다.

**Files:**
- Modify: `backend/src/main/java/com/msg/backend/domain/quiz/application/QuizService.java:265-306` (`executeRun` 주석과 반영 트랜잭션)
- Test: `backend/src/test/java/com/msg/backend/domain/quiz/api/QuizSafetyApiTest.java`
- Modify: `docs/backend-guide.md:47` (5절 "만들기·채점은 전체 시간 예산…" 문단)
- Modify: `docs/wiki/backend.md`, `docs/wiki/README.md` 목차 (교훈 한 건)

**Interfaces:** 없음 (private 메서드 안의 수정, `QuizGradingRun.getStatus()`는 Lombok `@Getter`로 이미 있다).

- [ ] **Step 1: 실패하는 테스트 추가** — `QuizSafetyApiTest`의 "채점 한도·중복" 묶음에 추가. 처음 채점 A가 모델을 기다리는 동안(가짜 모델 응답 함수 안에서) regrade가 한 일을 DB로 재현한다: 시계를 3분 넘기고, A를 TIMED_OUT으로 끝내고, B를 RUNNING으로 넣는다(답은 submit이 이미 PENDING·GRADING으로 저장해 두었고 regrade도 같은 값으로 표시하므로 그대로 둔다).

```java
@Test
void 늦게_끝난_채점_실행은_그사이_시작된_다시_채점의_답을_건드리지_않는다() throws Exception {
    long quizId = generate();
    List<Long> ids = questionIds(quizId);
    java.util.concurrent.atomic.AtomicBoolean replaced = new java.util.concurrent.atomic.AtomicBoolean();
    Function<LlmRequest, LlmResponse> normal = grader(List.of("met", "met"));
    for (LlmProvider provider : LlmProvider.values()) {
        fakeLlm.get(provider).respond(request -> {
            if (request.system().contains("채점 보조자") && replaced.compareAndSet(false, true)) {
                clock.advance(Duration.ofMinutes(3));
                long runA = jdbcTemplate.queryForObject("select id from quiz_grading_run", Long.class);
                long submission = jdbcTemplate.queryForObject(
                        "select submission_id from quiz_grading_run", Long.class);
                jdbcTemplate.update("update quiz_grading_run set status = 'TIMED_OUT', finished_at = ? where id = ?",
                        Timestamp.valueOf(LocalDateTime.now(clock)), runA);
                insertRuns(submission, 1, "RUNNING", clock.instant().plusSeconds(60));
            }
            return normal.apply(request);
        });
    }

    long submissionId = submissionId(submit(quizId, List.of(Map.of("questionId", ids.get(2), "text", "PAT 설명")))
            .andExpect(status().isCreated()));

    // B가 맡은 답은 그대로 GRADING이어야 B가 결과를 반영할 수 있다
    assertThat(jdbcTemplate.queryForObject("select grading_note from quiz_answer where question_id = ?",
            String.class, ids.get(2))).isEqualTo("GRADING");
    assertThat(jdbcTemplate.queryForList("select status from quiz_grading_run order by id", String.class))
            .containsExactly("TIMED_OUT", "RUNNING");

    // B도 끝나지 못하고 마감이 지나면 다시 채점으로 정상 채점된다
    clock.advance(Duration.ofMinutes(5));
    enableGraders(List.of("met", "met"));
    regrade(quizId, submissionId)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.results[2].gradingStatus").value("graded"));
}
```

- [ ] **Step 2: 실패 확인** — `cd backend && ./gradlew test --tests "*QuizSafetyApiTest"` → 새 테스트가 `expected "GRADING" but was "MODEL_FAILED"`로 FAIL.
- [ ] **Step 3: 구현** — 반영 트랜잭션에서 `run`을 읽은 직후, `run == null || run.getStatus() != QuizGradingRun.Status.RUNNING`이면 답·제출 합계·실행 상태를 아무것도 바꾸지 않고 끝낸다(그 실행의 답은 `expireStaleRuns`가 이미 정리했거나 실행이 지워졌다). RUNNING이면 지금 코드 그대로(마감 전이면 반영, 지났으면 MODEL_FAILED + TIMED_OUT). `executeRun` 주석에 "이미 끝난(다른 요청이 정리한) 실행은 답을 건드리지 않는다. 한 제출의 GRADING 답을 맡은 실행은 하나뿐이라 실행 id를 답에 따로 두지 않는다" 한 줄을 더한다. 로그는 `log.info("이미 끝난 채점 실행이라 결과를 반영하지 않는다 (run {})", runId)` 정도로 답 내용 없이.
- [ ] **Step 4: 통과 확인** — `./gradlew test --tests "*QuizSafetyApiTest" --tests "*QuizApiTest"` → PASS(기존 `마감이_지난_뒤_도착한_채점은_반영하지_않고_다시_채점할_수_있게_둔다`, `같은_제출을_채점하는_중이면_409이고_마감이_지난_실행은_정리하고_다시_채점한다` 포함). 이어서 `./gradlew pgTest --tests "*QuizSafetyApiTest"` → PASS.
- [ ] **Step 5: 문서** — `docs/backend-guide.md` 47행 문장 끝에 "채점 실행이 저장할 때 이미 끝나 있으면(다시 채점이 마감 지난 실행을 정리한 뒤) 답을 건드리지 않는다"를 붙인다. `wiki-lesson` 스킬로 `docs/wiki/backend.md`에 한 건(제목 예: "늦게 끝난 채점 실행이 그사이 시작된 다시 채점의 답을 실패로 바꿨다", 원인: 반영 조건이 실행 상태가 아니라 답 상태만 봤다, 다음에는: 비동기 결과를 반영할 때는 "내 실행이 아직 주인인가"를 먼저 본다)과 README 목차 줄을 더한다. 날짜 2026-11-27, PR 번호는 머지 때.
- [ ] **Step 6: 커밋**

```bash
git add backend/src/main/java/com/msg/backend/domain/quiz/application/QuizService.java \
  backend/src/test/java/com/msg/backend/domain/quiz/api/QuizSafetyApiTest.java \
  docs/backend-guide.md docs/wiki/backend.md docs/wiki/README.md
git commit -m "fix: 늦게 끝난 채점 실행이 다시 채점 중인 답을 건드리지 않게"
```

### Task 2: 제출 채점도 잠근 뒤 동의를 다시 확인한다

**문제:** `QuizService.submit`(159행)은 `blockedReason(userId)`를 잠금 밖에서만 본다. 그 뒤 동의 철회(`users.ai_quiz_consent=false`)가 커밋되고 나서 잠금을 얻어도 그대로 채점 실행을 예약하고 모델을 부른다. `regrade`(221행 잠금 → 236행 확인)는 잠근 뒤 확인한다.

**Files:**
- Modify: `backend/src/main/java/com/msg/backend/domain/quiz/application/QuizService.java:165-170` (`submit`의 저장 트랜잭션 안 `lockLoginUser` 다음)
- Test: `backend/src/test/java/com/msg/backend/domain/quiz/api/QuizSafetyApiTest.java`

**Interfaces:** 없음.

- [ ] **Step 1: 실패하는 테스트 추가** — "AI 퀴즈 처리 동의" 묶음에. `@Autowired PlatformTransactionManager transactionManager`를 테스트 클래스에 더한다. 다른 스레드가 사용자 행을 잠근 채 기다리는 동안 제출을 보내고(제출은 잠금 밖 동의 확인을 통과한 뒤 잠금에서 기다린다), 그 스레드가 동의를 끄고 커밋한다.

```java
@Test
void 제출이_잠금을_기다리는_동안_동의를_철회하면_모델을_부르지_않고_대기로_둔다() throws Exception {
    long quizId = generate();
    enableGraders(List.of("met", "met"));
    List<Long> ids = questionIds(quizId);
    int before = fakeLlm.get(LlmProvider.GPT).requests().size();
    CountDownLatch locked = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
        Future<?> withdraw = pool.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(s -> {
            userRepository.lockLoginUser(userId);
            locked.countDown();
            sleep(500);
            jdbcTemplate.update("update users set ai_quiz_consent = false where id = ?", userId);
        }));
        locked.await(5, TimeUnit.SECONDS);
        Future<String> submitted = pool.submit(() -> submit(quizId,
                List.of(Map.of("questionId", ids.get(2), "text", "PAT 설명")))
                .andReturn().getResponse().getContentAsString());
        withdraw.get(30, TimeUnit.SECONDS);
        String body = submitted.get(30, TimeUnit.SECONDS);

        assertThat((String) JsonPath.read(body, "$.data.results[2].gradingStatus")).isEqualTo("pending");
        assertThat((String) JsonPath.read(body, "$.data.results[2].gradingMessage"))
                .contains("AI 퀴즈 처리에 동의하지 않아");
    } finally {
        pool.shutdownNow();
    }
    assertThat(fakeLlm.get(LlmProvider.GPT).requests()).hasSize(before);
    assertThat(count("quiz_grading_run")).isZero();
}
```

(`sleep`은 `InterruptedException`을 `IllegalStateException`으로 바꾸는 작은 도우미로 테스트 클래스 아래에 둔다. `CountDownLatch`·`ExecutorService`·`Executors`·`Future`·`TimeUnit`·`TransactionTemplate`·`PlatformTransactionManager` import.)

- [ ] **Step 2: 실패 확인** — `./gradlew test --tests "*QuizSafetyApiTest"` → 새 테스트가 `gradingStatus` `graded`(또는 GPT 요청 수 증가)로 FAIL. 통과해 버리면 제출 스레드가 잠금 전에 동의를 읽지 못한 것이니 `sleep` 값을 늘려 FAIL을 먼저 본다.
- [ ] **Step 3: 구현** — 저장 트랜잭션에서 `userRepository.lockLoginUser(userId)` 바로 다음에 `note = blockedReason(userId);`를 두고, `note == null`일 때만 `note = limitReason(userId);`. 잠근 뒤 `findById`는 잠금 조회가 막 읽은 같은 엔티티를 돌려주므로(open-in-view false, 이 트랜잭션의 새 영속성 컨텍스트) 최신 동의 값을 본다. 잠금 밖 첫 확인은 그대로 둔다(보낼 수 없으면 개인정보 검사를 건너뛰는 용도). 주석: "잠금 밖에서 본 뒤 철회됐을 수 있어 잠근 뒤 다시 본다(regrade와 같은 순서)".
- [ ] **Step 4: 통과 확인** — `./gradlew test --tests "*QuizSafetyApiTest" --tests "*QuizApiTest"` → PASS. `./gradlew pgTest --tests "*QuizSafetyApiTest"` → PASS.
- [ ] **Step 5: 커밋** — `git commit -m "fix: 퀴즈 제출 채점도 사용자 행을 잠근 뒤 AI 퀴즈 동의를 다시 확인"` (두 파일).

### Task 3: 대화 삭제도 사용자 행을 잠근다

**문제:** `ConversationService.delete`(104-111행)는 잠금 없이 `analysisCleanup.deleteRunsUsingConversation`을 부른다. 같은 사용자의 `AnalysisService.prepare`가 잠금 안에서 `analysis_run`·`analysis_run_conversation`을 넣고 아직 커밋하지 않았으면 그 분석이 안 보인다. PostgreSQL에서는 대화 행 삭제가 외래 키 잠금으로 분석 커밋을 기다렸다가 `analysis_run_conversation`만 CASCADE로 지우고 `analysis_run`은 남아, 지운 대화로 만든 판단이 저장된다(`save`는 실행 행이 있으니 막지 않는다). 가져오기(`importConversations`)와 분석 시작은 이미 같은 잠금을 쓴다.

**Files:**
- Modify: `backend/src/main/java/com/msg/backend/domain/conversation/application/ConversationService.java:104-111` (`delete` 첫 줄, 위 주석)
- Test: `backend/src/test/java/com/msg/backend/domain/conversation/api/ConversationApiTest.java`

**Interfaces:**
- Consumes (테스트): `AnalysisRun.start(Long userId, Long courseId, String promptVersion, int conversationCount, int messageCount, int truncatedMessageCount, int inputChars, LocalDateTime now)`, `AnalysisRunConversation.of(Long runId, Long conversationId)`, `AnalysisRunRepository`, `AnalysisRunConversationRepository`(`domain/analysis/dao`).

- [ ] **Step 1: 실패하는 테스트 추가** — "삭제" 근처에. `@Autowired`로 `AnalysisRunRepository`, `AnalysisRunConversationRepository`, `PlatformTransactionManager`를 더한다. 분석 시작(`prepare`)이 하는 일을 다른 스레드의 트랜잭션으로 재현한다: 잠그고, 실행과 쓴 대화 행을 넣고, 잠시 뒤 커밋.

```java
@Test
void 분석이_시작되는_중에_대화를_지우면_그_분석도_지워진다() throws Exception {
    send(token, importBody(UUID.randomUUID().toString(), 1, 1, courseId, List.of(simple("c-1"))))
            .andExpect(status().isCreated());
    long conversationId = jdbcTemplate.queryForObject("select id from conversation", Long.class);
    long userId = jdbcTemplate.queryForObject("select id from users where email = 'me@example.com'", Long.class);
    CountDownLatch started = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
        Future<?> analysis = pool.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(s -> {
            userRepository.lockLoginUser(userId);
            AnalysisRun run = runRepository.saveAndFlush(
                    AnalysisRun.start(userId, courseId, "test", 1, 1, 0, 10, LocalDateTime.now()));
            runConversationRepository.saveAndFlush(AnalysisRunConversation.of(run.getId(), conversationId));
            started.countDown();
            sleep(300);
        }));
        started.await(5, TimeUnit.SECONDS);
        Future<Integer> deleted = pool.submit(() -> mockMvc.perform(delete("/api/conversations/" + conversationId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))).andReturn().getResponse().getStatus());
        analysis.get(30, TimeUnit.SECONDS);
        assertThat(deleted.get(30, TimeUnit.SECONDS)).isEqualTo(200);
    } finally {
        pool.shutdownNow();
    }
    assertThat(count("conversation")).isZero();
    assertThat(count("analysis_run")).isZero();
}
```

(`count`는 이 클래스에 이미 있다. `sleep` 도우미는 Task 2와 같은 모양으로 이 클래스에도 둔다.)

- [ ] **Step 2: 실패 확인** — `./gradlew pgTest --tests "*ConversationApiTest"` → 새 테스트가 `analysis_run` 1로 FAIL. `./gradlew test --tests "*ConversationApiTest"`(H2)도 돌려 본다(H2는 CASCADE 대기 동작이 달라 409·500 같은 다른 이유로 실패할 수 있다. 어느 쪽이든 FAIL이면 된다).
- [ ] **Step 3: 구현** — `delete`의 첫 줄에 `userRepository.lockLoginUser(userId);`. 주석을 "사용자 행을 잠가 같은 사용자의 분석 시작과 차례로 처리한다(잠금 없이 지우면 커밋 전 분석이 안 보여 지운 대화로 만든 분석이 남는다)"로 고친다.
- [ ] **Step 4: 통과 확인** — `./gradlew test --tests "*ConversationApiTest" --tests "*AnalysisApiTest"` → PASS. `./gradlew pgTest --tests "*ConversationApiTest"` → PASS.
- [ ] **Step 5: 커밋** — `git commit -m "fix: 대화 삭제도 사용자 행을 잠가 진행 중인 분석 시작과 겹치지 않게"`.
- [ ] **Step 6: PR 1 마무리** — `./gradlew build` PASS, `./gradlew pgTest` PASS. PR 본문에 "API·DTO 변경 없음"과 지적 5를 뺀 이유를 적는다(푸시·PR은 사용자가 요청할 때).

---

## PR 2 `fix/backend-small-bugs` (PR 1 머지 뒤 main에서)

### Task 4: 서술형 피드백은 과반 의견을 낸 모델의 것

**문제:** `AnswerGradingService.essay`(294행)는 `judged.get(0)`(늘 GPT부터인 `EnumMap` 순서)의 피드백을 쓴다. GPT가 소수 의견이면 점수와 반대 방향의 피드백이 보인다.

**Files:**
- Modify: `backend/src/main/java/com/msg/backend/domain/quiz/application/AnswerGradingService.java:264-301` (`essay`), 새 package-private static 메서드
- Test: `backend/src/test/java/com/msg/backend/domain/quiz/application/AnswerGradingRulesTest.java`, `backend/src/test/java/com/msg/backend/domain/quiz/api/QuizSafetyApiTest.java`
- Modify: `docs/backend-guide.md:48` (5절 "주관식 의미 판단·서술형 채점은…" 문단)

**Interfaces:**
- Produces: `static int feedbackModelIndex(List<List<RubricLevel>> levelsByModel, List<RubricLevel> majorities)` — 기준별 과반 수준(`majorities`, 과반이 없으면 그 칸 null)과 같은 수준을 가장 많이 낸 모델의 순번. 같으면 앞 순번. 과반이 하나도 없으면 0.

- [ ] **Step 1: 실패하는 테스트 추가** — 규칙 단위 테스트:

```java
@Test
void 피드백은_과반_수준과_가장_많이_맞은_모델에서_가져오고_같으면_앞_모델() {
    var met = RubricLevel.MET;
    var not = RubricLevel.NOT_MET;
    var partial = RubricLevel.PARTIAL;
    assertThat(AnswerGradingService.feedbackModelIndex(
            List.of(List.of(not, not), List.of(met, met), List.of(met, met)), List.of(met, met))).isEqualTo(1);
    assertThat(AnswerGradingService.feedbackModelIndex(
            List.of(List.of(met, partial), List.of(met, not)), Arrays.asList(met, null))).isZero();
    assertThat(AnswerGradingService.feedbackModelIndex(
            List.of(List.of(met), List.of(partial), List.of(not)), Arrays.asList((RubricLevel) null))).isZero();
}
```

  그리고 연결 확인용 통합 테스트(`QuizSafetyApiTest`, "AI 하나만" 묶음 앞): GPT는 `not_met, not_met` + `"strengths": "소수 의견"`, CLAUDE·GEMINI는 `met, met` + `"strengths": "다수 의견"`으로 답하게 하고, 서술형 답 제출 결과의 `$.data.results[2].aiFeedback.strengths`가 `"다수 의견"`, `gradingStatus`가 `graded`인지 본다. 이름 `서술형_피드백은_과반_의견을_낸_모델의_것을_보여_준다`. `grader(levels)`를 `grader(levels, strengths)`로 넓히고(서술형 노드에 `strengths`가 null이 아니면 `put`) 기존 `grader(levels)`는 `grader(levels, null)`을 부르게 한다.
- [ ] **Step 2: 실패 확인** — `./gradlew test --tests "*AnswerGradingRulesTest"` → 컴파일 실패(메서드 없음). 통합 테스트는 Step 4에서 `"소수 의견"`이 아닌지로 확인된다(지금 코드는 `"소수 의견"`).
- [ ] **Step 3: 구현** — `feedbackModelIndex`를 위 Interfaces대로 만들고, `essay`의 기준 반복에서 기준마다 정한 `level`을 `majorities` 목록에 모은 뒤 `EssayJudgment feedback = judged.get(feedbackModelIndex(judged.stream().map(EssayJudgment::levels).toList(), majorities));`로 바꾼다. 메서드 위 주석: "피드백은 점수와 같은 방향이어야 하므로 과반 수준을 가장 많이 낸 모델의 것을 쓴다(같으면 앞 모델)".
- [ ] **Step 4: 통과 확인** — `./gradlew test --tests "*AnswerGradingRulesTest" --tests "*QuizSafetyApiTest" --tests "*QuizApiTest"` → PASS.
- [ ] **Step 5: 문서** — `docs/backend-guide.md` 48행 "과반 의견만 쓰며" 뒤에 "서술형 피드백도 과반 수준을 가장 많이 낸 모델의 것을 쓰며"를 넣는다.
- [ ] **Step 6: 커밋** — `git commit -m "fix: 서술형 피드백을 과반 의견 모델에서 가져오기"`.

### Task 5: 서비스 전체 상한에 막힌 호출은 사용자 몫을 돌려준다

**문제:** `ModelInvoker.invoke`(116-121행)는 사용자 몫(`tryAcquireForUser`)을 먼저 받아 간 뒤 서비스 전체(`tryAcquire`)에 막히면 모델을 부르지 않고 `DAILY_LIMIT`을 돌려준다. 사용자 몫 한 번이 부르지도 않은 호출로 사라진다. 순서 자체는 의도다(몫을 다 쓴 사용자가 전체 상한을 줄이지 않게, `사용자_몫을_다_쓰면_부르지_않고_서비스_전체_상한도_줄이지_않는다`).

**Files:**
- Modify: `backend/src/main/java/com/msg/backend/global/llm/AiCallQuota.java` (기본 메서드 추가, 클래스 주석)
- Modify: `backend/src/main/java/com/msg/backend/global/llm/DbAiCallQuota.java` (구현)
- Modify: `backend/src/main/java/com/msg/backend/global/llm/UserAiCallDailyRepository.java` (쿼리 추가)
- Modify: `backend/src/main/java/com/msg/backend/global/llm/ModelInvoker.java:116-121`
- Test: `backend/src/test/java/com/msg/backend/global/llm/ModelInvokerDeadlineTest.java`, `backend/src/test/java/com/msg/backend/global/llm/UserAiCallLimitApiTest.java`
- Modify: `docs/backend-guide.md:41` (4절 "하루 AI 호출 상한" 문단)

**Interfaces:**
- Produces: `AiCallQuota.releaseForUser(Long userId)` — `default void`, 아무것도 하지 않음. "`tryAcquireForUser`로 받아 갔지만 서비스 전체 상한에 막혀 부르지 않은 한 번을 돌려준다".
- Produces: `UserAiCallDailyRepository.decrementIfPositive(Long userId, LocalDate usageDate)` → `int` — `@Modifying(flushAutomatically = true, clearAutomatically = true)`, `update UserAiCallDaily u set u.callCount = u.callCount - 1 where u.userId = :userId and u.usageDate = :usageDate and u.callCount > 0`.

- [ ] **Step 1: 실패하는 테스트 추가**
  - `ModelInvokerDeadlineTest`: `서비스_전체_상한에_막히면_받아_간_사용자_몫을_돌려준다` — 사용자 몫은 늘 true, `tryAcquire`는 false, `releaseForUser` 호출 수를 `AtomicInteger`로 세는 상한으로 `invoke(7L, client, …)`. 기대: `errorCode() == DAILY_LIMIT`, `attempts() == 0`, 모델 호출 0, 돌려주기 1.
  - `UserAiCallLimitApiTest`: `돌려주면_오늘_줄이_하나_줄고_줄이_0이면_돌려줘도_0이다` — `fill(userId, 2); quota.releaseForUser(userId);` → `todayCount(userId) == 1`; `fill(userId, 0); quota.releaseForUser(userId);` → `todayCount(userId) == 0`.
- [ ] **Step 2: 실패 확인** — `./gradlew test --tests "*ModelInvokerDeadlineTest" --tests "*UserAiCallLimitApiTest"` → 컴파일 실패(`releaseForUser` 없음).
- [ ] **Step 3: 구현** — 위 Interfaces대로 추가. `DbAiCallQuota.releaseForUser`는 `!properties.userEnabled() || userId == null`이면 그냥 돌아가고, 아니면 `newTransaction()`(REQUIRES_NEW) 안에서 `decrementIfPositive(userId, today())`. `ModelInvoker`에서 `!quota.tryAcquire()`일 때 `quota.releaseForUser(userId)`를 부른 뒤 `DAILY_LIMIT`을 돌려준다. 루프 위 주석과 `AiCallQuota` 클래스 주석에 "전체 상한에 막히면 받아 간 사용자 몫을 돌려준다"를 더한다.
- [ ] **Step 4: 통과 확인** — `./gradlew test --tests "*ModelInvokerDeadlineTest" --tests "*UserAiCallLimitApiTest" --tests "*AiDailyLimitApiTest"` → PASS. `./gradlew pgTest --tests "*UserAiCallLimitApiTest"` → PASS.
- [ ] **Step 5: 문서** — `docs/backend-guide.md` 41행 "사용자 몫 → 서비스 전체 순서로 한 번씩 받아 가고," 뒤에 "서비스 전체에 막히면 받아 간 사용자 몫은 돌려주며,"를 넣는다.
- [ ] **Step 6: 커밋** — `git commit -m "fix: 서비스 전체 AI 상한에 막혀 부르지 않은 호출은 사용자 몫을 돌려주기"`.

### Task 6: 탈퇴할 때 저장 키가 없는 자료는 파일 정리에서 뺀다

**문제:** `WithdrawalService.withdraw`(79-81행)는 `Material::getStorageKey`를 거르지 않고 넘긴다. `material.storage_key`는 NULL 허용(원본만 지우는 정책용), `file_deletion_task.storage_key`는 NOT NULL이라 null 키가 하나라도 있으면 탈퇴가 DB 오류로 실패한다. 자료 삭제(`MaterialService.deleteAll`, 170행)는 이미 `filter(Objects::nonNull)`.

**Files:**
- Modify: `backend/src/main/java/com/msg/backend/domain/user/application/WithdrawalService.java:79-81`
- Test: `backend/src/test/java/com/msg/backend/domain/user/api/WithdrawalApiTest.java`

**Interfaces:** 없음.

- [ ] **Step 1: 실패하는 테스트 추가** — `원본을_지운_자료가_있어도_탈퇴된다`: `me@example.com`의 id를 읽고 `insert into material (user_id, title, original_file_name, file_format, content_type, file_size, storage_key, status, created_at, updated_at) values (?, '노트', '노트.pdf', 'PDF', 'application/pdf', 10, null, 'UPLOADED', ?, ?)`로 저장 키 없는 자료를 넣은 뒤 `withdraw(token, PASSWORD)` → `status().isOk()`, 그 사용자 행과 `material` 행이 0, `file_deletion_task`에 새 행 없음.
- [ ] **Step 2: 실패 확인** — `./gradlew test --tests "*WithdrawalApiTest"` → 새 테스트가 409(또는 500)로 FAIL.
- [ ] **Step 3: 구현** — `.map(Material::getStorageKey)` 다음에 `.filter(Objects::nonNull)`(`java.util.Objects` import). 주석 없이 `MaterialService.deleteAll`과 같은 모양.
- [ ] **Step 4: 통과 확인** — `./gradlew test --tests "*WithdrawalApiTest" --tests "*SocialWithdrawalApiTest"` → PASS.
- [ ] **Step 5: 커밋** — `git commit -m "fix: 탈퇴 때 저장 키가 없는 자료는 파일 정리 작업에서 빼기"`.

### Task 7: 파일 정리 재시도는 가장 오래 시도하지 않은 것부터

**문제:** `FileCleanupService.retryPending`(88행)은 늘 `findTop100ByOrderByIdAsc`라, 계속 실패하는 작업이 100건 쌓이면 그 뒤 작업은 영영 시도되지 않는다. `file_deletion_task`에는 V1부터 `attempt_count`·`last_attempted_at`이 있고 실패할 때 `recordFailure`가 채운다.

**판단:** 다음 시도 시각(백오프) 열은 만들지 않는다. 순서만 "마지막 시도가 오래된 것(시도한 적 없으면 만든 시각)부터"로 바꾸면 실패 작업이 뒤로 돌아가 모든 작업이 차례로 시도된다. 실패 작업이 많아 로그가 늘어나는 것은 10분 주기·100건이라 감당할 만하다.

**Files:**
- Modify: `backend/src/main/java/com/msg/backend/global/storage/FileDeletionTaskRepository.java` (`findTop100ByOrderByIdAsc`를 바꿈)
- Modify: `backend/src/main/java/com/msg/backend/global/storage/FileCleanupService.java:84-92` (`retryPending`, 클래스 주석 3번)
- Create: `backend/src/test/java/com/msg/backend/global/storage/FileCleanupRetryTest.java`
- Modify: `docs/backend-design.md:101` (`file_deletion_task` 설명)

**Interfaces:**
- Produces: `FileDeletionTaskRepository.findRetryBatch(Pageable pageable)` → `List<FileDeletionTask>` — `@Query("select t from FileDeletionTask t order by coalesce(t.lastAttemptedAt, t.createdAt), t.id")`. (NULL 정렬 위치가 H2·PostgreSQL에서 다를 수 있어 `nulls first` 대신 `coalesce`.)
- Produces: `FileCleanupService.RETRY_BATCH_SIZE = 100` (package-private `static final int`).

- [ ] **Step 1: 실패하는 테스트 추가** — `@IntegrationTest class FileCleanupRetryTest`(`FileCleanupService`, `JdbcTemplate`, `MutableClock` 주입, `@BeforeEach`에서 `clock.reset()`과 `delete from file_deletion_task`):

```java
@Test
void 계속_실패하는_작업이_한_번에_가져오는_양만큼_쌓여도_뒤_작업을_지운다() {
    LocalDateTime now = LocalDateTime.now(clock);
    // 저장소 key 형식이 아니라 LocalFileStorage.delete가 늘 실패하는 작업
    for (int i = 0; i < FileCleanupService.RETRY_BATCH_SIZE; i++) {
        insertTask("bad key " + i, now.minusHours(2));
    }
    insertTask("materials/1/gone.pdf", now.minusHours(1));

    assertThat(fileCleanupService.retryPending()).isZero();
    clock.advance(Duration.ofMinutes(10));

    assertThat(fileCleanupService.retryPending()).isEqualTo(1);
    assertThat(jdbcTemplate.queryForObject(
            "select count(*) from file_deletion_task where storage_key = 'materials/1/gone.pdf'", Integer.class))
            .isZero();
    // 실패한 작업은 지워지지 않고 시도 횟수만 쌓인다
    assertThat(jdbcTemplate.queryForObject("select count(*) from file_deletion_task", Integer.class))
            .isEqualTo(FileCleanupService.RETRY_BATCH_SIZE);
}
```

  (`insertTask(key, createdAt)`은 `insert into file_deletion_task (storage_key, attempt_count, created_at) values (?, 0, ?)`.) 이 Task 전까지는 `RETRY_BATCH_SIZE` 대신 `100`을 쓰고 Step 3에서 상수로 바꾼다.
- [ ] **Step 2: 실패 확인** — `./gradlew test --tests "*FileCleanupRetryTest"` → 두 번째 `retryPending()`이 0이라 FAIL.
- [ ] **Step 3: 구현** — 위 Interfaces대로 저장소 메서드를 바꾸고, `retryPending`은 `taskRepository.findRetryBatch(PageRequest.of(0, RETRY_BATCH_SIZE))`. 클래스 주석 3번에 "마지막 시도가 오래된 것부터(계속 실패하는 작업이 뒤 작업을 막지 않게)"를 더한다.
- [ ] **Step 4: 통과 확인** — `./gradlew test --tests "*FileCleanupRetryTest" --tests "*MaterialApiTest"` → PASS(`MaterialApiTest`의 `retryPending()` 기대값 그대로). `./gradlew pgTest --tests "*FileCleanupRetryTest"` → PASS.
- [ ] **Step 5: 문서** — `docs/backend-design.md` 101행 "실패하면 남아서 `app.storage.cleanup-retry-interval`(10분)마다 다시 지운다." 뒤에 "한 번에 100건, 마지막 시도가 오래된 것부터다."를 붙인다.
- [ ] **Step 6: 커밋** — `git commit -m "fix: 파일 정리 재시도를 마지막 시도가 오래된 작업부터"`.
- [ ] **Step 7: PR 2 마무리** — `./gradlew build` PASS, `./gradlew pgTest` PASS. PR 본문에 "API·DTO·마이그레이션 변경 없음"을 적는다(푸시·PR은 사용자가 요청할 때).
