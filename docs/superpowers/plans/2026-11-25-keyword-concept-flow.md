# 키워드 개념으로 커리큘럼 끝내기 (#212) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 자료 본문 전송이 꺼진 운영 설정에서도 과목 키워드 개념으로 커리큘럼 STEP 2를 지나가고, 진도·상태 문구가 실제 상태를 말하게 한다.

**Architecture:** 서버 단계 판정 규칙(`CurriculumFlowBuilder.need()`)은 그대로 두고, 서버 STEP 2 요약 문장 하나와 프론트 화면(커리큘럼·대시보드·자료·평가)만 바꾼다. 키워드 개념 진도는 커리큘럼 응답의 `flow.concepts[].practiceGradedCount`로 프론트 도우미 하나가 센다. DB·API 응답 형식은 바꾸지 않는다.

**Tech Stack:** Spring Boot 3 / Java 17 (JUnit, MockMvc), React 19 + TypeScript, Vitest.

**Spec:** `docs/superpowers/specs/2026-11-25-keyword-concept-flow-design.md`

## Global Constraints

- DB 마이그레이션·API 응답 필드 추가/변경 없음.
- UI 문구·주석은 한국어, 식별자는 영어. "정확도" 표현 금지.
- 진도 숫자·막대는 코드가 센 값이라 `primary`(파랑). AI 보라(`ai`) 금지. 색 코드 직접 쓰기 금지.
- 브랜치 `fix/keyword-concept-flow`. 화면·문구를 확인받으며 고치는 작업이라 **작업 중에는 커밋하지 않고**, 화면 확인 뒤 한 번에 커밋한다(루트 CLAUDE.md). 커밋 메시지 예: `fix: 자료 본문 전송이 꺼져 있어도 키워드 개념으로 커리큘럼 STEP 2를 지나가게`.
- 자료 개념이 있는 과목(목업 정보보호개론, `concept.study !== null`) 화면은 바뀌면 안 된다.

## Review Focus

- 자료 개념과 키워드 개념이 섞인 과목(사전평가 개념 일부만 자료 개념): 자료 개념 카드는 지금처럼 "개념 보기", 키워드 카드만 "퀴즈로 확인하기". → Task 3 화면 확인 항목.
- 전송이 **켜져** 있지만 개념을 아직 안 뽑은 과목: 키워드 카드는 퀴즈 버튼이지만, 맨 위 안내는 "핵심 개념 탭에서 뽑기" 문구를 유지. → Task 3.
- 과목 키워드도 자료 개념도 없는 과목(`concepts.length === 0`): 진도 칸은 지금 문구("자료에서 핵심 개념을 뽑으면…") 대신 빈 상태 문구 그대로, 0으로 나누지 않음. → Task 2 테스트 `total 0이면 percent null`.
- 채점 대기만 있는 키워드 개념(`practiceGradedCount 0`, `practiceStatus 'pending'`): 확인한 개념으로 세지 않는다(채점 대기를 결과로 세지 않는 기존 규칙). → Task 2 테스트.
- 사전평가 화면에서 전송이 꺼져 체크 칸을 숨겨도 `useContent`가 `false`로 남아 요청에 `useMaterialContent: true`가 가지 않는다. → Task 5.

---

### Task 1: 서버 STEP 2 요약 문장

**Files:**
- Modify: `backend/src/main/java/com/msg/backend/domain/curriculum/application/CurriculumFlowBuilder.java:326-328` (`studySummary`)
- Test: `backend/src/test/java/com/msg/backend/domain/curriculum/api/CurriculumFlowApiTest.java`

**Interfaces:** 없음 (문장만, `FlowStage.summary` 필드 그대로).

- [ ] **Step 1: 실패하는 테스트 추가**

```java
@Test
void 자료_개념이_없으면_STEP2_요약이_퀴즈로_확인하라고_안내한다() throws Exception {
    upload("노트.txt");
    curriculum()
            .andExpect(jsonPath("$.data.flow.conceptSource").value("course"))
            .andExpect(jsonPath("$.data.flow.stages[1].summary")
                    .value(containsString("퀴즈로 확인해요")))
            .andExpect(jsonPath("$.data.flow.stages[1].summary")
                    .value(not(containsString("핵심 개념을 뽑으면"))));
}
```

(파일의 기존 테스트처럼 `org.hamcrest.Matchers.containsString`·`not`을 쓴다.)

- [ ] **Step 2: 실패 확인** — `cd backend && ./gradlew test --tests "*CurriculumFlowApiTest"` → 새 테스트 FAIL.
- [ ] **Step 3: 구현** — `progress.studiableConcepts() == 0`일 때 반환을 `head + " 자료로 공부한 뒤 개념마다 퀴즈로 확인해요. 퀴즈를 풀면 다음 단계로 넘어가요."`로 바꾼다. 클래스 주석의 단계 설명은 그대로 맞다.
- [ ] **Step 4: 통과 확인** — 같은 명령 → PASS. 이어서 `./gradlew test` 전체 PASS.

### Task 2: 키워드 개념 퀴즈 확인 수 도우미

**Files:**
- Modify: `frontend/src/utils/conceptStudy.ts`
- Test: `frontend/src/utils/conceptStudy.test.ts`

**Interfaces:**
- Produces: `keywordQuizProgress(concepts: Pick<FlowConcept, 'study' | 'practiceGradedCount'>[]): { total: number; checked: number; percent: number | null }` — `total` = `study === null`인 개념 수, `checked` = 그중 `practiceGradedCount > 0`, `percent` = `total === 0 ? null : Math.round(checked * 100 / total)`. `@/utils` 배럴로 이미 내보내진다.

- [ ] **Step 1: 실패하는 테스트**

```ts
describe('keywordQuizProgress', () => {
  it('학습 기록이 없는 키워드 개념만 세고, 채점된 퀴즈가 있는 개념을 확인한 것으로 센다', () => {
    expect(keywordQuizProgress([
      { study: null, practiceGradedCount: 2 },
      { study: null, practiceGradedCount: 0 },
      { study: null, practiceGradedCount: 0 }, // 채점 대기만 있어도 세지 않음
      { study: studied, practiceGradedCount: 3 }, // 자료 개념은 제외
    ])).toEqual({ total: 3, checked: 1, percent: 33 });
  });
  it('키워드 개념이 없으면 percent는 null', () => {
    expect(keywordQuizProgress([])).toEqual({ total: 0, checked: 0, percent: null });
  });
});
```
(`studied`는 테스트 파일 안에서 만든 `ConceptStudyResponse` 형 객체 하나, 필요한 필드만 `as` 캐스팅.)

- [ ] **Step 2: 실패 확인** — `cd frontend && npx vitest run src/utils/conceptStudy.test.ts` → FAIL (`keywordQuizProgress` 없음).
- [ ] **Step 3: 구현** — 위 Interfaces대로.
- [ ] **Step 4: 통과 확인** — 같은 명령 → PASS.

### Task 3: 커리큘럼 STEP 2 화면

**Files:**
- Modify: `frontend/src/pages/curriculum/CurriculumPage.tsx` (`StudyProgressBar` 258-287, `ConceptStudyStep` 289-380, `CourseCurriculum` 797)
- Modify: `frontend/src/mocks/curriculum.ts:194-201` (STEP 2 요약을 서버와 같은 문장으로)

**Interfaces:**
- Consumes: `keywordQuizProgress` (Task 2), `conceptQuizLink(courseId, name, mode)` (기존), `useQuizAiStatus()` (`hooks/useAiStatus.ts`, `data.materialContentEnabled`).
- Produces: `ConceptStudyStep({ flow, courseId, mode })` — `CourseCurriculum`이 `courseId`·`data.recommendedMode`를 넘긴다.

- [ ] **Step 1:** `ConceptStudyStep` 카드: `concept.study === null`이면 칩 "퀴즈로 확인 (과목 키워드)", 카드 안내 `<p className="text-caption text-ink-soft">올린 자료로 이 개념을 공부한 뒤 퀴즈로 확인해요.</p>`, 버튼은 `conceptQuizLink(courseId, concept.name, mode)`로 가는 "퀴즈로 확인하기"(`ListChecks` 아이콘, priority면 primary). 자료 개념(`study !== null`) 카드는 지금 그대로. 키워드 카드는 늘 퀴즈 버튼이 있으므로 `conceptLink`가 null일 때의 "이 과목에 자료를 올리면…" 문구 분기는 지운다(자료 개념은 `materialId`가 늘 있어 그 분기를 타지 않는다).
- [ ] **Step 2:** 맨 위 키워드 안내(`conceptSource === 'course'`): `materialContentEnabled`가 false면 "지금은 자료에서 개념을 뽑지 않아 과목 키워드로 공부해요. 퀴즈를 하나 풀면 다음 단계로 넘어가요.", true면 지금 문구 유지.
- [ ] **Step 3:** `StudyProgressBar`: `studiableConcepts === 0`이고 `keywordQuizProgress(flow.concepts).total > 0`이면 `ScoreBar value={percent}` + "과목 키워드 {total}개 중 **{checked}개를 퀴즈로 확인했어요** ({percent}%)". 둘 다 0이면 지금 문구.
- [ ] **Step 4:** 목업 STEP 2 요약: `progress.studiableConcepts === 0`이면 끝에 " 자료로 공부한 뒤 개념마다 퀴즈로 확인해요. 퀴즈를 풀면 다음 단계로 넘어가요."를 붙인다(서버 Task 1과 같은 문장).
- [ ] **Step 5: 확인** — `npm run lint && npx tsc -b`(또는 `npm run build`) 통과. 화면 확인은 Task 6.

### Task 4: 대시보드 학습 진도

**Files:**
- Modify: `frontend/src/pages/analysis/components/SummaryStrip.tsx:155-200` (`ProgressTile`)
- Modify: `frontend/src/pages/analysis/components/StudyProgressPanel.tsx:23-28`

**Interfaces:** Consumes `keywordQuizProgress` (Task 2).

- [ ] **Step 1:** `ProgressTile`: 자료 개념이 없고(`studiableConcepts === 0`) 키워드 `total > 0`이면 큰 숫자 `{checked} / {total}개`, `ScoreBar`, 캡션 "퀴즈로 확인 {percent}% (과목 키워드)", "자세히" 링크 유지. 키워드도 0이면 "공부할 개념이 아직 없어요".
- [ ] **Step 2:** `StudyProgressPanel`: 같은 조건에서 막대 + "과목 키워드 {total}개 중 {checked}개를 퀴즈로 확인했어요"와 한 줄 설명 "자료에서 개념을 뽑지 않아 학습 완료 기록 없이 퀴즈 결과로만 세요." 다섯 칸 통계(`dl`)는 자료 개념이 있을 때만.
- [ ] **Step 3: 확인** — `npm run lint` 통과.

### Task 5: 자료·평가 화면 문구

**Files:**
- Modify: `frontend/src/pages/materials/MaterialListPage.tsx:53-56` (`StatusChip` `uploaded`)
- Modify: `frontend/src/pages/materials/MaterialDetailPage.tsx:23-30` (`NotReady` `uploaded`)
- Modify: `frontend/src/pages/assessments/AssessmentPage.tsx:~240-274` (본문 사용 체크 칸)

- [ ] **Step 1:** 목록 `uploaded` → "저장됨".
- [ ] **Step 2:** 상세 `NotReady` `uploaded`: 제목 "파일을 올렸어요", 설명 "이 자료로 만든 퀴즈가 아직 없어요. 퀴즈 만들기에서 이 자료의 과목 개념으로 퀴즈를 만들 수 있어요. 올린 파일은 지우기 전까지 보관해요."
- [ ] **Step 3:** 평가 화면: `!ai.materialContentEnabled`면 "자료 본문으로 문제 만들기" 라벨(체크 칸 + 설명)과 그 아래 확인 체크를 그리지 않는다. `useContent` 초기값 false가 그대로라 요청의 `useMaterialContent`가 true가 되지 않음을 코드로 확인. 전송이 켜져 있을 때는 지금 그대로.
- [ ] **Step 4: 확인** — `npm run lint` 통과.

### Task 6: 전체 확인과 마무리

- [ ] **Step 1:** `cd frontend && npm run lint && npm run format && npm run format:check && npm test && npm run build` 모두 통과.
- [ ] **Step 2:** `cd backend && ./gradlew test` 통과.
- [ ] **Step 3: 화면 확인(목업)** — 개발 서버에서 체험 모드: 키워드뿐인 과목의 커리큘럼 STEP 2(퀴즈 버튼·안내·진행 막대), 대시보드 학습 진도 칸·자세한 진도, 자료 목록 "저장됨", 사전·사후평가 체크 칸 없음. 정보보호개론(자료 개념) 커리큘럼·대시보드가 바뀌지 않았는지. 스크린샷으로 사용자에게 확인받는다.
- [ ] **Step 4: 실제 서버 흐름 확인(로컬 백엔드, 전송 꺼짐 기본값)** — 키워드 과목에 자료 올리기 → 사전평가 제출 → STEP 2 "퀴즈로 확인하기" → 퀴즈 하나 풀기 → 커리큘럼에서 STEP 2 done·STEP 3 current. AI 키가 없으면 이 단계는 못 한다고 보고한다.
- [ ] **Step 5:** 발표 정리 표(`0_notes/MSG/presentation-quality-security.md`)에 문제→해결 한 줄 추가.
- [ ] **Step 6:** 사용자 확인 뒤 한 번에 커밋(spec·plan 문서 포함), PR 본문에 `Closes #212`와 화면 변경(제출 자료 갱신 필요 여부) 적기.
