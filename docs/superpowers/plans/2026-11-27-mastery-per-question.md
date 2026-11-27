# 숙련도 퀴즈 근거를 문제마다 세기 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 숙련도 점수의 퀴즈 근거를 명세대로 "문제마다 s = 그 문제 점수 ÷ 그 문제 만점"으로 세어, 만점이 큰 서술형 하나가 결과를 좌우하지 않게 한다.

**Architecture:** 개념별 퀴즈 집계(`QuizAssessor.byConcept`, Java 코드 집계라 새 쿼리·DB 문법 없음)가 채점 끝난 문제마다 득점률을 더한 `rateSum`과 그 문제 수 `ratedCount`를 함께 낸다. 이 두 값이 `ConceptQuizStatsResponse`에 실려 `MasteryCalculator`로 가고, 계산기는 `Σw·s = quizWeight × rateSum`, `Σw = quizWeight × ratedCount`로 계산한다. 산식이 바뀌므로 `app.mastery.formula-version`을 `"2"`로 올리고, 화면의 퀴즈 비율 문구를 산식 버전에 맞춘다.

**Tech Stack:** Spring Boot 3.5 / Java 17 (JUnit 5, AssertJ, MockMvc), React 19 + TypeScript, Vitest.

**Spec:** `docs/frontend-requests.md` 5-10절 "숙련도 점수 산식 설계안"(657줄~). 퀴즈 객관식 s = 1/0, 주관식·서술형 s = 채점된 점수 ÷ 만점, w = 1.0(`app.mastery.quiz-weight`), 점수 = (Σw·s + 1) ÷ (Σw + 2), 채점 대기·확인 필요 답은 뺀다, 산식이 바뀌면 `formula_version`을 올린다.

**고치는 문제(재현):** `MasteryCalculator.calculate`(`MasteryCalculator.java:64-75`)가 전체 비율 `quizScore ÷ quizMaxScore` 하나를 `quizGradedCount`개 문제 모두에 같은 s로 준다(호출 `AnalysisQueryService.java:208`). 객관식 2문제 0/1 + 서술형 1문제 10/10이면 명세 Σw·s = 1, Σw = 3 → 2/5 = **0.40 보통**, 지금 코드 Σw·s = 3 × 10/12 = 2.5 → 3.5/5 = **0.70 강점**.

## Global Constraints

- 브랜치 `fix/mastery-per-question`(main에서). 커밋 메시지는 `fix: 한국어 설명` + 끝에 `Co-Authored-By` 줄. 한 PR에 이 기능 하나만.
- API 응답이 바뀐다(`ConceptQuizStatsResponse`에 `ratedCount`·`rateSum` 추가, `mastery.quizRate` 뜻 변경). **백엔드 DTO와 프론트 타입(`frontend/src/types/quiz.ts`·`analysis.ts`)을 같은 PR에서** 고친다. PR 전에 `api-contract-checker` 에이전트로 대조한다.
- DB 마이그레이션 없음(`concept_mastery`의 열은 그대로, `formula_version`은 값만 `"2"`). 머지된 `V*.sql`은 고치지 않는다.
- 점수는 코드 규칙으로만 계산한다. 숫자(가중치·기준·경계)는 `app.mastery.*` 설정값 그대로 두고 새 숫자를 코드에 박지 않는다.
- 쿼리·집계는 JPA로 읽은 엔티티를 Java로 센다. DB 전용 문법(PostgreSQL 함수, 네이티브 쿼리)을 쓰지 않는다(H2·PostgreSQL 모두 통과).
- `MasteryProperties`(설정 record)에 생성자를 더하지 않는다(`docs/wiki/backend.md` "설정 record에 생성자를 하나 더 만들었더니 앱이 뜨지 않는다").
- 다른 사람의 계약 테스트는 고치지 않는다. 새 필드 이름에 `correct`·`concept`·`aiExplanation`·`해설`을 넣지 않는다(`QuizApiTest:141`, `QuizGenerationApiTest:120`의 금지 단어, 위키 "응답에 새 필드를 더했더니 다른 사람의 계약 테스트가 깨졌다").
- UI 문구·주석·문서는 한국어, 식별자는 영어. "정확도"·"진단" 표현 금지. 화면에 숫자 점수(`score`)를 보여 주지 않는다.
- 문서에 경과(날짜·PR 번호)를 새로 쓰지 않는다(위키 교훈 한 건은 위키 규칙대로).
- 화면 문구(Task 3)는 사용자에게 화면으로 확인받은 뒤 커밋한다(루트 `CLAUDE.md`).
- 작업 후 통과: `cd backend && ./gradlew build`, `cd backend && ./gradlew pgTest`(로컬 PostgreSQL `msg_test`), `cd frontend && npm run lint && npm run format:check && npm test && npm run build`.
- 범위 밖: 보완 개념 판정(`weak`)·검증 상태(`ConceptVerification`)·커리큘럼 단계가 쓰는 전체 점수 비율(`score ÷ maxScore`), 동시성 문제, 대화 신호를 메시지 단위로 세기(미결 TODO 그대로).

## Review Focus

- **개념이 둘인 문제**(서술형 개념 `NAT`·`VLAN`): 그 문제의 s가 두 개념에 한 번씩 들어간다(지금 `byConcept`가 점수를 두 개념에 모두 더하는 규칙과 같음). → Task 1 API 테스트의 `vlan` 단언.
- **채점 대기·확인 필요 답만 있는 개념:** `ratedCount 0`, `rateSum 0` → 퀴즈 근거 없음, `quizRate null`, 근거 부족이면 `quizNeeded`가 나온다. → Task 1 `채점_대기와_확인_필요_답은_문제별_득점률에_넣지_않는다`, Task 2 기존 `근거가_하나도_없으면_근거_부족이다`.
- **만점이 0인 채점 답**(정상 생성은 서술형 기준 2~6개 × 2~5점이라 나오지 않지만 데이터 이상): s를 정할 수 없어 `ratedCount`·`rateSum` 모두에서 뺀다(`gradedCount`에는 지금처럼 남는다). → Task 1 `만점이_0인_답은_문제별_득점률에_넣지_않는다`.
- **점수가 만점보다 큰 답**(데이터 이상): s를 0~1로 자른다. → Task 1 같은 테스트.
- **v1 기록과 v2 기록이 섞인 변화 추이:** "한 단계 올랐어요"를 숨기고 기록 문장에 "산식 v1 → v2"를 쓰며, v1 기록의 비율은 예전 문구("얻은 점수")로 보인다. 산식 버전만 바뀌어도(일부 모델 실패 분석 포함) 새 기록이 남는다. → Task 2 `MasteryHistoryServiceTest`, Task 3 `mastery.test.ts`.

---

### Task 1: 개념별 퀴즈 집계에 문제별 득점률 합 더하기

**Files:**
- Modify: `backend/src/main/java/com/msg/backend/domain/quiz/application/QuizAssessor.java` (`ConceptStat` 45-51, `byConcept` 148-172)
- Modify: `backend/src/main/java/com/msg/backend/domain/quiz/dto/ConceptQuizStatsResponse.java`
- Modify: `backend/src/main/java/com/msg/backend/domain/quiz/application/QuizStatsService.java` (`byCourse` 65-79의 생성자 호출, 클래스 주석의 "숙련도 점수는 미결이라 만들지 않는다" 문장)
- Modify: `frontend/src/types/quiz.ts` (`ConceptQuizStats` 274-280)
- Test: `backend/src/test/java/com/msg/backend/domain/quiz/application/QuizAssessorTest.java`
- Test: `backend/src/test/java/com/msg/backend/domain/quiz/api/QuizGenerationApiTest.java` (`개념별_퀴즈_결과와_이미_알아요_확인_결과를_준다_피드백만으로는_바뀌지_않는다` 334-370)

**Interfaces:**
- Produces: `QuizAssessor.ConceptStat(String conceptKey, String name, int questionCount, int gradedCount, int correctCount, int score, int maxScore, int pendingCount, int reviewCount, boolean weak, int ratedCount, double rateSum)` — 새 두 값은 **맨 끝에** 붙인다(같은 int 칸 사이에 끼워 순서가 밀리지 않게). `ratedCount` = `GRADED`이고 그 답의 `maxScore() > 0`인 문제 수, `rateSum` = 그 문제마다 `clamp(score ÷ maxScore, 0, 1)`의 합.
- Produces: `ConceptQuizStatsResponse(..., java.time.LocalDateTime lastSubmittedAt, int ratedCount, double rateSum)` — 지금 13칸 뒤에 두 칸. `@Schema`: `ratedCount` "숙련도에 넣은 문제 수 (채점이 끝나고 만점이 있는 문제)", `rateSum` "그 문제마다 (얻은 점수 ÷ 그 문제 만점)을 더한 값. 숙련도 계산에 쓴다".
- Produces (프론트): `ConceptQuizStats`에 `ratedCount: number;`·`rateSum: number;`(주석 한 줄씩). `QuizConceptStat`(채점 결과 화면용)은 바꾸지 않는다 — `QuizResultResponse.ConceptStat`은 `QuizAssessor.ConceptStat`을 필드별로 옮겨 담아(`QuizResultResponse.java:135`) 응답이 바뀌지 않는다.

- [ ] **Step 1: 실패하는 단위 테스트** — `QuizAssessorTest`에 추가. 서술형은 기존 테스트처럼 `QuizQuestion.essay(1L, 3, "서술", List.of(QuizRubricItem.of("기준", 5), QuizRubricItem.of("기준2", 5)), "모범", null, null, List.of(QuizConcept.of("nat", "NAT")))`(만점 10)로 만든다.

```java
@Test
void 만점이_다른_문제가_섞여도_문제마다_득점률을_한_번씩_더한다() {
	// 객관식 2문제 0/1 + 서술형 1문제 10/10 → 점수 합 10/12지만 문제별 득점률 합은 0+0+1
	var stat = QuizAssessor.byConcept(List.of(mc("nat", false), mc("nat", false),
			new QuizAssessor.Graded(essay10("nat"), GradingStatus.GRADED, 10, 10, null)), 0.6).get(0);

	assertThat(stat.score()).isEqualTo(10);
	assertThat(stat.maxScore()).isEqualTo(12);
	assertThat(stat.ratedCount()).isEqualTo(3);
	assertThat(stat.rateSum()).isCloseTo(1.0, within(1e-9));
}

@Test
void 채점_대기와_확인_필요_답은_문제별_득점률에_넣지_않는다() {
	var stat = QuizAssessor.byConcept(List.of(mc("nat", true),
			new QuizAssessor.Graded(essay10("nat"), GradingStatus.PENDING, 0, 10, GradingNotes.NOT_CONFIGURED),
			new QuizAssessor.Graded(essay10("nat"), GradingStatus.NEEDS_REVIEW, 4, 10, null)), 0.6).get(0);

	assertThat(stat.ratedCount()).isEqualTo(1);
	assertThat(stat.rateSum()).isCloseTo(1.0, within(1e-9));
}

@Test
void 만점이_0인_답은_문제별_득점률에_넣지_않고_만점보다_큰_점수는_1로_자른다() {
	var stat = QuizAssessor.byConcept(List.of(
			new QuizAssessor.Graded(essay10("nat"), GradingStatus.GRADED, 0, 0, null),
			new QuizAssessor.Graded(essay10("nat"), GradingStatus.GRADED, 12, 10, null)), 0.6).get(0);

	assertThat(stat.gradedCount()).isEqualTo(2);
	assertThat(stat.ratedCount()).isEqualTo(1);
	assertThat(stat.rateSum()).isCloseTo(1.0, within(1e-9));
}
```

(`essay10(String concept)`는 위 서술형을 돌려주는 private 도우미. `within`은 `org.assertj.core.api.Assertions.within`.)

- [ ] **Step 2: 실패 확인** — `cd backend && ./gradlew test --tests "*QuizAssessorTest"` → 컴파일 실패(`ratedCount()`·`rateSum()` 없음).
- [ ] **Step 3: 구현** — `byConcept`에서 개념마다 `int[]` 옆에 `Map<String, Double>`(또는 `double[]`)로 `rateSum`을 모으고, `GRADED`이고 `answer.maxScore() > 0`일 때만 `ratedCount++`·`rateSum += clamp((double) score / maxScore)`. 기존 칸(`score`·`maxScore`·`weak` 등)의 값은 바꾸지 않는다. `ConceptQuizStatsResponse`·`QuizStatsService.byCourse` 생성자 호출에 두 값을 넘긴다(`stat == null`이면 `0`, `0.0`). 프론트 타입 두 칸 추가.
- [ ] **Step 4: 통과 확인** — 같은 명령 → PASS.
- [ ] **Step 5: API 단언 추가** — 기존 `개념별_퀴즈_결과와_…` 테스트의 제출 뒤 `concept-stats` 요청에 붙인다. 이 테스트의 `NAT`는 객관식 1/1, 주관식 1/1, 서술형 미응답 0/6이라 점수 합 2/8(지금 단언)이고 문제별 득점률 합은 2.0이다.

```java
.andExpect(jsonPath("$.data[0].ratedCount").value(3))
.andExpect(jsonPath("$.data[0].rateSum").value(2.0))
.andExpect(jsonPath("$.data[1].ratedCount").value(1))   // vlan: 서술형 하나(개념 둘인 문제)
.andExpect(jsonPath("$.data[1].rateSum").value(0.0))
```

- [ ] **Step 6: 통과 확인** — `cd backend && ./gradlew test --tests "*QuizAssessorTest" --tests "*QuizGenerationApiTest" --tests "*QuizApiTest"` → PASS(`QuizApiTest`의 금지 단어 계약이 그대로 통과하는지 함께 본다).
- [ ] **Step 7: 커밋**

```bash
git add backend/src/main/java/com/msg/backend/domain/quiz backend/src/test/java/com/msg/backend/domain/quiz frontend/src/types/quiz.ts
git commit -m "fix: 개념별 퀴즈 결과에 문제별 득점률 합 더하기"
```

### Task 2: 숙련도 계산을 문제별 득점률로 바꾸고 산식 버전 올리기

**Files:**
- Modify: `backend/src/main/java/com/msg/backend/domain/analysis/application/MasteryCalculator.java` (`calculate` 58-97, `Result` 주석의 `quizGradedCount`·`quizRate`)
- Modify: `backend/src/main/java/com/msg/backend/domain/analysis/application/AnalysisQueryService.java` (`mastery` 206-215)
- Modify: `backend/src/main/java/com/msg/backend/domain/analysis/dto/AnalysisRunResponse.java` (`Mastery.quizGradedCount`·`quizRate`, `MasteryRecord.quizRate`의 `@Schema` 설명)
- Modify: `backend/src/main/resources/application.yaml` (`app.mastery.formula-version: "1"` → `"2"`, 145줄)
- Modify: `frontend/src/types/analysis.ts` (`ConceptMastery.quizRate`·`MasteryRecord.quizRate` 주석)
- Test: `backend/src/test/java/com/msg/backend/domain/analysis/application/MasteryCalculatorTest.java`
- Test: `backend/src/test/java/com/msg/backend/domain/analysis/application/MasteryHistoryServiceTest.java`
- Test: `backend/src/test/java/com/msg/backend/domain/analysis/api/AnalysisApiTest.java` (151줄 `formulaVersion` 기대값)

**Interfaces:**
- Consumes: Task 1의 `ConceptQuizStatsResponse.ratedCount()`·`rateSum()`.
- Produces: `MasteryCalculator.calculate(int quizRatedCount, double quizRateSum, int conversationCount, UnderstandingStatus conversationStatus): Result` — 지금 5인자 메서드를 대신한다. `quizRatedCount > 0`이면 `Σw += quizWeight × quizRatedCount`, `Σw·s += quizWeight × quizRateSum`, `quizRate = clamp(quizRateSum ÷ quizRatedCount)`(문제별 평균 득점률). `Result.quizGradedCount`에는 `quizRatedCount`를 담는다(뜻 "세어 넣은 퀴즈 문제 수" 그대로).
- Produces: `MasteryCalculator.calculate(ConceptQuizStatsResponse quiz, int conversationCount, UnderstandingStatus conversationStatus): Result` — `quiz`가 null이면 `(0, 0.0, …)`, 아니면 `(quiz.ratedCount(), quiz.rateSum(), …)`로 위 메서드를 부른다. 응답의 `score`·`maxScore`를 숙련도에 쓰지 않는다는 연결을 이 메서드 하나에 모아 단위 테스트로 지킨다. `AnalysisQueryService.mastery`는 `masteryCalculator.calculate(quiz, conversationCount, item.getStatus())`만 부른다.
- **`quizRate` 결정:** 문제별 평균 득점률(Σs ÷ 문제 수)로 둔다. 화면(`MasteryEvidence.tsx:52-56`, `utils/mastery.ts:42-46`)이 단계 옆에 이 값을 "반영한 근거"로 보여 주는데, 점수 합 비율을 두면 재현 예시에서 "83%"인데 단계는 "보통"이라 근거와 단계가 어긋나 보인다. 평균 득점률이면 33%로 단계 계산과 같은 값이다. 문구는 Task 3에서 버전별로 맞춘다.

- [ ] **Step 1: 기존 예시 테스트를 새 시그니처로 옮기기** — `MasteryCalculatorTest`의 모든 `calculator.calculate(n, score, max, c, status)`를 `calculator.calculate(n, rateSum, c, status)`로 바꾼다. 객관식 예시(5-10절 표, 경계값, 근거 부족)는 `max == n`이라 `rateSum = score`(double)로 옮기면 기대값이 그대로다. `주관식_부분_점수는_득점률로_센다`만 이름을 `주관식_부분_점수는_문제마다_득점률로_센다`로 바꾸고 `calculate(3, 1.5, 0, null)`(세 문제 모두 1/2)로, 기대값(`quizRate` 0.5, `score` 0.5, NORMAL)은 그대로.
- [ ] **Step 2: 새 테스트 추가**

```java
@Test
void 만점이_다른_문제가_섞여도_문제마다_한_번씩_센다() {
	// 객관식 2문제 0/1 + 서술형 1문제 10/10 → Σw·s = 1, Σw = 3, (1+1)/(3+2) = 0.4
	var result = calculator.calculate(3, 1.0, 0, null);

	assertThat(result.score()).isCloseTo(0.4, within(1e-9));
	assertThat(result.level()).isEqualTo(Level.NORMAL);
	assertThat(result.quizRate()).isCloseTo(1.0 / 3.0, within(1e-9));
	assertThat(result.quizGradedCount()).isEqualTo(3);
}

@Test
void 퀴즈_결과_응답으로_계산하면_점수_합_비율이_아니라_문제별_득점률_합을_쓴다() {
	// 점수 합은 10/12(83%)라 예전 계산은 0.70 강점이었다
	var quiz = new ConceptQuizStatsResponse("nat", "NAT", 3, 3, 1, 10, 12, 0, 0, false, null, null, null, 3, 1.0);

	var result = calculator.calculate(quiz, 0, null);

	assertThat(result.score()).isCloseTo(0.4, within(1e-9));
	assertThat(result.level()).isEqualTo(Level.NORMAL);
}

@Test
void 퀴즈_결과가_없으면_대화만으로_계산한다() {
	assertThat(calculator.calculate(null, 6, UnderstandingStatus.UNDERSTOOD).score())
			.isCloseTo(0.8, within(1e-9));
}
```

`MasteryHistoryServiceTest`에 산식 버전만 다른 경우를 더한다(도우미 `mastery(...)`는 `"1"` 고정이라 버전을 받는 겹도우미를 하나 둔다):

```java
@Test
void 산식_버전만_바뀌어도_기록한다() {
	var v2 = new AnalysisRunResponse.Mastery("normal", null, 0, 3, 0.67, 1, "understood", "2", null, List.of());

	assertThat(MasteryHistoryService.shouldRecord("completed", LAST, v2)).isTrue();
	assertThat(MasteryHistoryService.shouldRecord("partial", LAST, v2)).isTrue();
}
```

- [ ] **Step 3: 실패 확인** — `cd backend && ./gradlew test --tests "*MasteryCalculatorTest" --tests "*MasteryHistoryServiceTest"` → 컴파일 실패(4인자·응답 인자 `calculate` 없음). `MasteryHistoryServiceTest`의 새 테스트는 지금 코드로도 통과해야 한다(기존 동작 확인용).
- [ ] **Step 4: 구현** — 위 Interfaces대로 `MasteryCalculator`를 바꾸고 5인자 메서드는 지운다. 클래스 주석의 퀴즈 줄은 이미 "문제마다 … 득점률(점수 ÷ 만점)"이라 그대로 두고, `Result`의 `quizRate` 주석을 "문제별 평균 득점률 (0~1)"로. `AnalysisQueryService.mastery`를 새 메서드로. `application.yaml` `formula-version: "2"`, 그 위 주석에 "퀴즈는 문제마다 s = 점수 ÷ 그 문제 만점" 한 구절. `AnalysisRunResponse`의 `quizRate` 설명 두 곳을 "문제별 평균 득점률 (0~1, 문제마다 점수 ÷ 만점의 평균). 퀴즈 근거가 없으면 null"로. 프론트 `types/analysis.ts`의 두 `quizRate`에 같은 뜻 주석. `AnalysisApiTest:151` 기대값 `"1"` → `"2"`.
- [ ] **Step 5: 통과 확인** — 같은 명령 → PASS. 이어서 `cd backend && ./gradlew test --tests "*AnalysisApiTest"` → PASS.
- [ ] **Step 6: 커밋**

```bash
git add backend/src frontend/src/types/analysis.ts
git commit -m "fix: 숙련도 퀴즈 근거를 문제별 득점률로 세고 산식 버전 2로 올리기"
```

### Task 3: 화면의 퀴즈 비율 문구를 산식 버전에 맞추기

**Files:**
- Modify: `frontend/src/utils/mastery.ts` (`describeMasteryChange` 42-46)
- Modify: `frontend/src/pages/analysis/components/MasteryEvidence.tsx` (52-56)
- Modify: `frontend/src/mocks/analysis.ts` (`record` 도우미 89줄과 `mastery.formulaVersion` 다섯 곳: 210·277·336·389·450줄 근처, 92-94줄 주석)
- Test: `frontend/src/utils/mastery.test.ts`

**Interfaces:**
- Consumes: Task 2의 `quizRate` 뜻(v2부터 문제별 평균 득점률, v1 기록은 점수 합 비율).
- Produces: `quizRateText(rate: number, formulaVersion: string): string` (`utils/mastery.ts`, `@/utils` 배럴로 나간다) — `formulaVersion === '1'`이면 `얻은 점수 ${pct}%`, 아니면 `문제별 평균 득점률 ${pct}%`(`pct = Math.round(rate * 100)`). 문구는 사용자 확인 대상(열린 질문).

- [ ] **Step 1: 실패하는 테스트** — `mastery.test.ts`에 추가. 기존 테스트(도우미 기본 `formulaVersion: '1'`)의 "얻은 점수 100%"·"얻은 점수 25%" 기대값은 그대로 통과해야 한다.

```ts
describe('quizRateText', () => {
  it('산식 v1 기록은 점수 합 비율, 그 뒤는 문제별 평균 득점률로 쓴다', () => {
    expect(quizRateText(0.25, '1')).toBe('얻은 점수 25%');
    expect(quizRateText(1 / 3, '2')).toBe('문제별 평균 득점률 33%');
  });
});

it('산식 v2 기록은 문제별 평균 득점률로 쓴다', () => {
  const result = describeMasteryChange(
    null,
    record({ quizGradedCount: 3, quizRate: 1 / 3, formulaVersion: '2' }),
  );
  expect(result.reasons).toEqual(['퀴즈 3문제(문제별 평균 득점률 33%)', '산식 v2']);
});
```

- [ ] **Step 2: 실패 확인** — `cd frontend && npx vitest run src/utils/mastery.test.ts` → FAIL(`quizRateText` 없음).
- [ ] **Step 3: 구현** — `quizRateText`를 만들고 `describeMasteryChange`의 `rate`와 `MasteryEvidence`의 "얻은 점수 …%" 칸이 이것을 쓴다(`MasteryEvidence`는 `mastery.formulaVersion`을 넘긴다). 목업: 숙련도 예시의 퀴즈 근거는 모두 1점 문제(`mocks/curriculum.ts` 근거 `score 2`·`maxScore 6`·`gradedCount 6` 등)라 v2로도 `score`·`quizRate` 값이 같으므로 `formulaVersion`만 `'2'`로 바꾸고, 92줄 주석에 "퀴즈는 문제마다 점수 ÷ 그 문제 만점" 한 구절을 넣는다.
- [ ] **Step 4: 통과 확인** — 같은 명령 → PASS. `describeLevelTrend`의 기존 "산식이 바뀌면 알리지 않는다" 테스트도 PASS(버전이 섞이면 "한 단계 올랐어요"를 숨기는 동작 확인).
- [ ] **Step 5: 확인** — `cd frontend && npm run lint && npm run format:check && npm test && npm run build` → 모두 통과.
- [ ] **Step 6: 화면 확인(커밋 전)** — `cd frontend && npm run dev` 체험 모드 대시보드에서 개념을 펼쳐 "반영한 근거"의 비율 문구와 "산식 버전 2", 변화 기록 문장을 스크린샷으로 사용자에게 확인받는다. 문구가 바뀌면 Step 1 기대값도 같이 고친다.
- [ ] **Step 7: 확인받은 뒤 커밋**

```bash
git add frontend/src/utils/mastery.ts frontend/src/utils/mastery.test.ts frontend/src/pages/analysis/components/MasteryEvidence.tsx frontend/src/mocks/analysis.ts
git commit -m "fix: 숙련도 근거의 퀴즈 비율 문구를 산식 버전에 맞추기"
```

### Task 4: 문서·위키·전체 검증과 PR 알림

**Files:**
- Modify: `docs/frontend-requests.md` 5-10절 "상태" 문단(659줄)
- Modify: `docs/frontend-guide.md` 78줄("얻은 점수 비율")
- Modify: `docs/backend-guide.md` 8절(65-71줄)
- Create/Modify: `docs/wiki/` 교훈 한 건 + `docs/wiki/README.md` 목차(`wiki-lesson` 스킬)

**Interfaces:** 없음.

- [ ] **Step 1: 5-10절 문장 고치기** — "상태" 문단에서 "단위 테스트 11개가 아래 계산 예시와 같은 값을 확인"을 "단위 테스트가 아래 계산 예시와 같은 값을 확인"으로(개수는 경과라 뺀다). `quizRate` 나열 뒤에 다음 문장을 넣는다: "퀴즈 근거는 채점이 끝난 문제마다 s = 그 문제 점수 ÷ 그 문제 만점으로 센다(만점이 0인 답은 뺀다). 그래서 `quizRate`는 문제별 평균 득점률이고 퀴즈 결과의 점수 합 비율(`score ÷ maxScore`)과 다를 수 있다. 개념별 퀴즈 결과(`ConceptQuizStats`)의 `ratedCount`·`rateSum`이 이 계산에 쓰는 문제 수와 득점률 합이다. 지금 산식 버전은 `2`." 계산 표·예시 표는 그대로(이미 문제 단위로 쓰여 있다).
- [ ] **Step 2: 가이드 두 곳** — `frontend-guide.md` 78줄 "얻은 점수 비율" → "문제별 평균 득점률(산식 v1 기록은 얻은 점수 비율)". `backend-guide.md` 8절에 한 줄: "퀴즈 근거는 `ConceptQuizStatsResponse.ratedCount`·`rateSum`(문제별 득점률 합)으로 센다. `score ÷ maxScore`를 숙련도에 쓰면 만점이 큰 서술형이 결과를 좌우한다(`MasteryCalculator.calculate(ConceptQuizStatsResponse, …)`)."
- [ ] **Step 3: 위키 교훈** — `wiki-lesson` 스킬로 `docs/wiki/backend.md`에 한 건: 상황(객관식 2문제 0/1 + 서술형 10/10이 "강점"), 원인(점수 합 비율 하나를 모든 문제에 같은 s로 줌), 해결(문제별 득점률 합, 산식 v2), 다음에는(가중 평균 산식에 합계 비율을 넣기 전에 만점이 다른 문제가 섞이는 예시를 테스트 표에 먼저 넣는다). 날짜·PR 번호는 위키 규칙대로.
- [ ] **Step 4: 전체 검증** — `cd backend && ./gradlew build` → BUILD SUCCESSFUL. `cd backend && ./gradlew pgTest` → BUILD SUCCESSFUL(로컬 `msg_test`가 없으면 `docs/setup.md` 순서대로 만들고, 못 하면 못 돌렸다고 보고). `cd frontend && npm run lint && npm run format:check && npm test && npm run build` → 통과.
- [ ] **Step 5: 계약 대조** — `api-contract-checker` 에이전트로 `ConceptQuizStatsResponse`·`AnalysisRunResponse.Mastery` 변경과 프론트 타입·목업·문서가 맞는지 확인.
- [ ] **Step 6: 커밋**

```bash
git add docs/frontend-requests.md docs/frontend-guide.md docs/backend-guide.md docs/wiki docs/superpowers/plans/2026-11-27-mastery-per-question.md
git commit -m "docs: 숙련도 퀴즈 근거를 문제마다 세는 규칙과 교훈 남기기"
```

- [ ] **Step 7: PR(사용자가 요청할 때만)** — 본문에 재현 예시(0.70 강점 → 0.40 보통), 응답 변경 두 가지, 화면 문구 변경(제출 자료 노션·영상의 숙련도 화면 갱신 필요 여부), PR 미리보기 체험 모드 확인 결과를 적는다. 백엔드 담당에게 알릴 한 줄(이름에 `@`를 붙이지 않는다): "주미 님, 개념별 퀴즈 결과에 `ratedCount`·`rateSum`이 늘었고(`/api/quizzes/concept-stats`, 분석 `concepts[].quiz`) `MasteryCalculator.calculate`가 문제별 득점률 합을 받도록 바뀌었으며, `app.mastery.formula-version`이 `2`가 되어 머지 뒤 첫 `runs/latest`에서 개념마다 숙련도 기록이 한 줄씩 새로 남아요."

### Task 5: 산식 버전에 숙련도 숫자 설정의 지문을 붙이기

숫자 설정(가중치·기준·경계)만 바꾸고 `formula-version`을 올리지 않으면 같은 버전 안에서 단계가 바뀌어 "한 단계 올랐어요"가 학습 변화처럼 보인다. 버전을 사람이 올리는 앞 번호 + 숫자 설정의 지문으로 만들어, 숫자만 바꿔도 버전이 저절로 달라지게 한다.

**Files:**
- Modify: `backend/src/main/java/com/msg/backend/domain/analysis/application/MasteryProperties.java` (메서드 `effectiveFormulaVersion()`, 시작 검증)
- Modify: `backend/src/main/java/com/msg/backend/domain/analysis/application/MasteryCalculator.java` (`Result.formulaVersion`에 이 값)
- Modify: `backend/src/main/java/com/msg/backend/domain/analysis/dto/AnalysisRunResponse.java` (`formulaVersion` `@Schema` 설명)
- Modify: `backend/src/main/resources/application.yaml` (`formula-version` 주석)
- Modify: `frontend/src/utils/mastery.ts` (`formulaMajor`, `quizRateText`의 v1 판정)
- Modify: `docs/frontend-requests.md` 5-10절, `docs/backend-guide.md` 8절 (같은 뜻 한 줄씩, 내용은 한 곳에만)
- Test: `backend/src/test/java/com/msg/backend/domain/analysis/application/MasteryPropertiesTest.java` (새 파일), `MasteryCalculatorTest`, `AnalysisApiTest`, `frontend/src/utils/mastery.test.ts`

**Interfaces:**
- Produces: `MasteryProperties.effectiveFormulaVersion(): String` — `formulaVersion + "-" + sha256(지문 문자열) 앞 8자리(16진수 소문자)`. 지문 문자열은 `quiz-weight`, `conversation-weight`, `understood-value`, `partial-value`, `not-understood-value`, `min-evidence-weight`, `needs-work-below`, `strong-from`을 이 순서로 `이름=Double.toString(값)`을 `;`로 이은 것. 예: `"2-a1b3c9d2"`. 생성자를 더하지 않고 메서드로 둔다(`docs/wiki/backend.md` "설정 record에 생성자를 하나 더 만들었더니 앱이 뜨지 않는다").
- 검증: record의 compact 생성자에서 `formulaVersion`이 비었거나 `-`가 들어 있거나, 만든 버전이 20자(`concept_mastery.formula_version VARCHAR(20)`)를 넘으면 `IllegalArgumentException` → 서버 시작 실패.
- 소비처: `MasteryCalculator.Result.formulaVersion` → 분석 응답 `mastery.formulaVersion`·`concept_mastery.formula_version` 기록이 모두 이 값을 쓴다.
- Produces (프론트): `formulaMajor(version: string): string`(`-` 앞 부분). `quizRateText`의 v1 판정은 `formulaMajor(v) === '1'`. "한 단계 올랐어요"를 숨기는 비교는 **전체 문자열** 비교 그대로(숫자만 바꿔도 비교하지 않게).

- [ ] **Step 1: 실패하는 테스트** — `MasteryPropertiesTest`: 같은 값이면 같은 버전, `quiz-weight`만 바꾸면 다른 버전, 앞 번호 유지(`2-` + 16진수 8자리), 길이 ≤ 20, 앞 번호에 `-`·빈 값·너무 긴 앞 번호는 생성 실패. 프론트 `mastery.test.ts`: `formulaMajor('2-a1b3c9d2') === '2'`, `formulaMajor('1') === '1'`, `quizRateText(0.25, '1-…')`은 v1 문구.
- [ ] **Step 2: 실패 확인** — `cd backend && ./gradlew test --tests "*MasteryPropertiesTest"` → 컴파일 실패. `cd frontend && npx vitest run src/utils/mastery.test.ts` → FAIL.
- [ ] **Step 3: 구현** — 위 Interfaces대로. `MasteryCalculatorTest`의 버전 단언은 `properties.effectiveFormulaVersion()`과 같고 `"1-"`로 시작하는지로, `AnalysisApiTest`는 `"2-"`로 시작하는지로 바꾼다. `application.yaml` 주석: "식 모양을 바꿀 때만 올린다. 숫자를 바꾸면 버전 뒤 지문이 저절로 바뀐다".
- [ ] **Step 4: 통과 확인** — `cd backend && ./gradlew test --tests "*Mastery*" --tests "*AnalysisApiTest"`, `cd frontend && npx vitest run src/utils/mastery.test.ts` → PASS. 앱 시작은 `./gradlew build`의 `@SpringBootTest`가 확인한다.
