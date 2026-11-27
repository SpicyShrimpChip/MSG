# 백엔드 기능 안내

`backend/CLAUDE.md`에서 옮긴 자세한 흐름이다. 지켜야 할 규칙은 `backend/CLAUDE.md`에 있고, 이 문서는 기능이 어떻게 동작하는지를 적는다. 테이블·설계 전체는 `docs/backend-design.md`, 프론트와 맞춘 API 계약은 `docs/frontend-requests.md`. 코드와 다르면 코드를 기준으로 이 문서를 고친다.

## 1. 인증·계정

- **Refresh Token 재사용 유예:** 회전된 지 `app.auth.refresh-reuse-grace`(10초) 안이고 세션이 살아 있으면 탭 여러 개의 동시 갱신으로 보고 다시 발급한다(`RefreshTokenService.isRotatedWithinGrace`).
- **탈퇴 뒤 30일 재가입 제한**(`RejoinRestriction`, `app.rejoin`, `withdrawn_account`): 탈퇴 트랜잭션에서 정규화한 이메일의 HMAC-SHA256(키 `REJOIN_HASH_SECRET`)과 재가입 가능 시각만 남기고(이메일 원문·users 연결 없음), 가입할 때 기간 안이면 409, 기간이 지난 기록은 주기 작업이 지운다. 키가 없으면 기록도 확인도 하지 않는다.
- **회원가입 봇 확인:** Cloudflare Turnstile(`SignupBotCheck`, `app.turnstile`). 비밀 키(`TURNSTILE_SECRET_KEY`)가 없으면 확인하지 않고(로컬·테스트), 운영 프로필은 `required: true`라 키가 없으면 서버가 뜨지 않는다. 확인 서버에 닿지 못하면 가입을 막는다(503). 같은 IP의 가입 시도는 `SignupAttemptLimiter`(`app.auth.signup-attempt`, 1시간 10번, 이메일 가입·카카오 가입 마무리 합계, 서버 메모리)로 제한한다.

### 카카오 로그인

`SocialAuthController`·`SocialLoginService`·`KakaoClient`, 설정 `app.oauth.kakao`·`app.frontend`. 계약은 `docs/frontend-requests.md` 5-11절.

- 브라우저가 `/api/auth/kakao/start` → 카카오 → `/api/auth/kakao/callback`으로 직접 이동하고, 끝나면 화면으로 302.
- state는 `msg_oauth_state_<state>` 쿠키(탭마다 따로, SameSite=Lax, 10분, 한 번만)로 확인하고 맞지 않으면 카카오를 부르지 않는다. 돌아갈 경로는 `ReturnTo.sanitize`(사이트 안 경로만).
- 연결된 카카오 회원번호면 로그인한다.
- 카카오가 **확인한** 같은 이메일의 비밀번호 회원이면 **자동 연결하지 않고** 같은 서명 쿠키를 주고 연결 확인 화면으로 보내 그 계정의 비밀번호를 확인한 뒤(`POST /api/auth/social/link`, 로그인과 같은 시도 제한) 연결한다(비밀번호 가입은 이메일 주인을 확인하지 않아 남이 미리 만든 계정일 수 있다).
- 처음 온 사용자는 **DB에 쓰지 않고** 서명한 가입 마무리 쿠키(`msg_social_signup`, JWT aud=social-signup, 10분)만 주고 약관 동의(`POST /api/auth/social/signup`) 때 계정과 `user_social_account`(회원번호만)를 만든다(카카오 정책: 가입 전 저장 금지).
- 카카오로만 가입한 계정은 `password_hash`가 NULL이라 비밀번호 로그인은 없는 계정과 같게 실패하고, 탈퇴는 확인 문구 "탈퇴"로 한다.
- 탈퇴가 커밋되면 카카오 연결 끊기(unlink, 어드민 키)를 부르고 실패해도 탈퇴는 되돌리지 않는다(트랜잭션 콜백이 아니라 컨트롤러가 서비스 반환 뒤에 부른다: `afterCommit`은 DB 연결을 반납하기 전이다).
- 콜백에서 가입을 거절할 때(이메일 없음·확인 안 된 이메일·같은 이메일 계정 충돌·재가입 제한)도 카카오 동의 순간 이미 생긴 연결을 끊는다(`SocialAuthController.handleOrUnlink`, 실패해도 안내는 같음). 가입 마무리 화면에서 그냥 나간 경우는 서버에 남는 것이 없어 끊지 않는다.
- 키(`KAKAO_*`)가 없으면 버튼 목록(`/api/auth/social/providers`)이 비고 시작 주소는 `socialError=unavailable`.

## 2. 자료 올리기

- 선택 `requestKey`(64자까지, `material.request_key`, UNIQUE `(user_id, request_key)`)를 받는다. 같은 사용자·같은 키면 사용자 행을 잠근 채 이미 올린 자료를 찾아 200으로 돌려주고 파일을 다시 저장하지 않는다.
- 사용자별 저장 용량 상한(`app.material.max-total-size` 200MB·`max-count` 100개, `MaterialQuotaProperties`): 올리기는 키가 없어도 사용자 행을 잠근 뒤 내 자료의 `file_size` 합계·개수를 세고, 넘으면 저장 전에 409로 거절한다. 같은 키 재전송은 상한보다 먼저 확인해 200을 돌려준다.

## 3. 마이그레이션 이력과 테스트 실행

- 지금 마이그레이션: `V1__init_schema.sql`·`V2__material_request_key.sql`·`V3__social_account.sql`(카카오 로그인)·`V4__user_ai_call_daily.sql`(사용자별 하루 AI 호출 몫)·`V5__evidence_based_learning.sql`(인지 수준·자료 근거·요약본·학습 행동).
- 운영 DB를 처음 만들기 전 V1~V16을 V1 하나로 합쳤다(두 번째 합치기, 합치기 전 스키마와 `pg_dump --schema-only`로 같은 것을 확인). 코드 주석의 "V6 퀴즈"·"V11" 같은 표기는 합치기 전 버전 이력이다.
- 테스트는 JVM 여러 개로 나눠 돈다: H2 `test`는 자동(CPU 절반, 최대 4개), `pgTest`는 `msgTestDbShards`(내 PC `~/.gradle/gradle.properties`, 또는 `TEST_DB_SHARDS`)를 주면 JVM마다 `msg_1_test`~`msg_N_test`를 쓴다(`TestDatabaseShard`, DB는 `db/create-test-db-shards.sh`). 주지 않으면 `msg_test` 하나. CI(4 vCPU)는 나누지 않는다(4·2·1개로 재 봤지만 실행마다 편차가 더 커서 이득을 확인하지 못함).

## 4. LLM 호출과 비용 상한

- **프롬프트 캐싱:** Claude는 `AnthropicClient`가 지시문(system) 끝에 `cache_control`을 붙이고, GPT·Gemini는 제공사가 자동으로 캐시한다. 저장하는 입력 토큰 수는 세 제공사 모두 캐시 포함 입력 전체다.
- **분석 시간 예산:** `app.analysis.deadline` 100초. 배포에서 Vercel 프록시가 첫 응답을 120초까지만 기다리고 프론트 timeout은 115초라 그보다 짧게 둔다. 늦은 모델은 시간 초과(실패)로 두고 나머지 결과를 저장한다.
- **사용자별 24시간 횟수**(`user_ai_usage`, `global/llm/UserAiUsage`): 분석 시작·퀴즈 만들기·AI 채점 실행 수는 실행 기록(`analysis_run` 등, 과목·제출에 CASCADE)이 아니라 `users`에만 연결한 이 기록으로 센다. 그래서 과목·대화·퀴즈를 지워도 한도가 다시 차지 않는다. 48시간 지난 행은 주기 작업이 지운다. `quiz_ai_call`도 만들기·채점 기록이 지워질 때 연결만 끊고(SET NULL) 남는다. 예외로 자료 개념 뽑기·개념 설명·자료 요약(`MaterialConceptService`·`MaterialSummaryService`)은 `quiz_ai_call` 행(모델 시도마다 한 줄)으로 24시간 횟수를 센다(실사용자 테스트 동안 비용을 보수적으로 세려고 유지).
- **하루 AI 호출 상한**(서비스 전체 `ai_daily_usage`, 사용자별 몫 `user_ai_call_daily`, `app.ai-daily-limit`): 서비스 전체(`calls`)와 사용자 한 명의 몫(`user-calls`, 모든 기능의 호출 합계, 계정 몇 개로 전체 상한을 다 쓰지 못하게)을 하루(한국 시간 날짜) 단위로 센다. `ModelInvoker`가 부를 때마다(다시 부를 때도) 사용자 몫 → 서비스 전체 순서로 한 번씩 받아 가고, 서비스 전체에 막히면 받아 간 사용자 몫은 돌려주며, 다 찼으면 부르지 않고 `USER_DAILY_LIMIT`·`DAILY_LIMIT`을 돌려준다. 분석·퀴즈 만들기·자료 개념 뽑기·개념 설명은 시작 전에 `ensureRemaining(userId, 부를 모델 수)`로 확인해 429를 준다(AI 채점은 `BUDGET_EXCEEDED` 대기로 남는다). `user_ai_call_daily`는 모델 스레드가 새 트랜잭션으로 쓰므로 `users`에 외래 키를 걸지 않고, 탈퇴 때 직접 지우며 지난 날짜는 주기 작업이 지운다. 서비스 전체 표는 사용자·과목과 연결하지 않아 과목 삭제·탈퇴·재가입으로 줄지 않는다.

## 5. 퀴즈

- 학습 모드(시험 대비·자기개발·업무 활용)마다 지시문이 다르다(`QuizPrompt`). 만들기는 설정된 첫 모델 하나만 부르고(`app.quiz.generation-providers`), `QuizGenerationParser`를 통과한 퀴즈만 저장한다.
- 비용 상한은 24시간 만들기 요청 수·AI 채점 실행 수(처음·다시 채점, `quiz_grading_run`)·실제 모델 요청 수(`quiz_ai_call` attempts 합) 세 겹이다.
- 만들기·채점은 전체 시간 예산(`generation-deadline`·`grading-deadline`) 안에서만 기다리고, 저장할 때 기록이 아직 진행 중·마감 전인지 확인해 늦은 결과는 버린다. 채점 실행이 저장할 때 이미 끝나 있으면(다시 채점이 마감 지난 실행을 정리한 뒤) 답을 건드리지 않는다. 모델 호출은 `ModelInvoker`의 QUIZ 스레드 묶음(분석과 분리).
- 주관식 의미 판단·서술형 채점은 설정된 모델 모두가 따로 판단하고 과반 의견만 쓰며(서술형 피드백도 과반 수준을 가장 많이 낸 모델의 것) 점수는 코드가 더한다(`AnswerGradingService`).
- 외부로는 과목 이름·개념·시험 범위·직무 분야·학습자 답(사후평가는 사전평가 문제 글도)만 보낸다.

## 6. 사전·사후평가·자료 핵심 개념

- 평가는 퀴즈 행(`assessment_kind`, `pre_assessment_id`)이고 사후평가는 최신 사전평가의 개념·개념 출처·유형별 수·난이도·모드를 그대로 쓴다(`QuizGenerationService`).
- 비교(`AssessmentService.compare`)는 최신 사전평가와 그 사후평가의 최근 제출에서 채점이 끝난 답만 세고, 변화량·개념 상태·비교 가능 조건을 코드로 계산한다.
- 본문 읽기는 `MaterialTextExtractor`(TXT·MD·PPTX·PDFBox, 외부 서비스 없음, 스캔 PDF는 NO_TEXT). 핵심 개념은 `MaterialConceptService`가 AI 후보 중 이름·근거 문장이 본문에 실제로 있는 것만 `material_concept`에 저장한다(지어낸 개념은 버림). 자세한 계약은 `docs/frontend-requests.md` 5-9절.

## 7. 근거 기반 학습

자세한 설계는 `docs/evidence-based-learning.md`.

- 퀴즈는 문제마다 인지 수준(개정 블룸, `BloomPlan`, 난이도와 별개)·출제 목표를 받고, 자료 본문을 쓰면 `SourceText`가 개념이 나오는 쪽·슬라이드·문단만 골라 보내고 문제별 인용을 **보낸 글 안에서** 문자열로 대조한다(위치는 서버가 찾음, 근거 확인 문제가 절반 미만이면 저장 안 함).
- 요약본(`MaterialSummaryService`, `material_summary`)은 항목마다 원문 근거·AI 보충을 나누고 같은 지시문 버전이면 다시 쓴다(자료 파일을 바꾸는 API가 없어 조회·재사용 때 파일 해시는 비교하지 않는다).
- 대화 분석 v2는 근거마다 학습 행동을 받고 상태와 맞지 않으면 근거 부족으로 낮춘다. 개념 검증 상태(`ConceptVerification`)의 "검증 완료"는 실제 확인 퀴즈가 같은 방향일 때만 준다.

## 8. 숙련도 기록

계산은 `analysis/application/MasteryCalculator`, 설정 `app.mastery.*`, 분석 응답 `concepts[].mastery`, 기록 `MasteryHistoryService`·`concept_mastery`. 산식은 `docs/frontend-requests.md` 5-10절.

- 최근 분석을 돌려줄 때(분석 실행·`runs/latest`) 개념의 마지막 기록과 단계·근거가 다르면 한 줄을 남기고 `mastery.history`(최근 30개)를 붙인다. 지난 분석(`runs/{id}`)은 기록하지 않고 붙이기만 한다.
- 판단한 모델이 없는 분석(`failed`)은 기록하지 않고 `runs/latest`의 결과도 되지 않으며(그 시도는 `lastAttempt`로 알림), 일부 모델이 실패한 분석(`partial`)은 퀴즈·산식이 바뀌었거나 첫 기록일 때만 남긴다.
- 기록은 `analysis_run`에 CASCADE로 달려 대화 삭제·동의 철회 때 함께 지워진다.
- 퀴즈 근거는 `ConceptQuizStatsResponse.ratedCount`·`rateSum`(문제별 득점률 합)으로 센다. `score ÷ maxScore`를 숙련도에 쓰면 만점이 큰 서술형이 결과를 좌우한다(`MasteryCalculator.calculate(ConceptQuizStatsResponse, …)`).
- 결과·기록·응답의 산식 버전은 `MasteryProperties.effectiveFormulaVersion()`(형식과 올리는 규칙은 5-10절)만 쓴다. 앞 번호에 `-`가 있거나 버전이 20자(`formula_version VARCHAR(20)`)를 넘으면 서버가 시작하지 않는다.
