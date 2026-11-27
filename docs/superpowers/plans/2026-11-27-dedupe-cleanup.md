# 중복·죽은 코드 정리 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 자료 본문을 AI로 보내는 확인 순서·막힌 이유·보내기 카드·모델 호출 꼬리·목업 저장·폼 부품의 중복을 하나로 모으고 죽은 코드를 지운다. 인용 확인 규칙 통일(PR 10) 하나만 동작을 바꾼다.

**Architecture:** PR 10개로 나눈다. 백엔드는 `MaterialAiGate`(보내기 전 확인·본문 읽기·개인정보 검사)를 먼저 만들고, 그 위에서 본문 추출을 사용자 행 잠금 밖으로 옮긴다. 프론트는 `utils/material.ts`의 막힌 이유 함수 → 공통 부품(`Checkbox`·`ErrorText message`) → `MaterialAiSendCard` 순으로 쌓는다. 모든 PR은 "지금 동작을 고정하는 테스트 확인·추가 → 옮기기 → 같은 테스트 통과" 순서다.

**Tech Stack:** Spring Boot 3 / Java 17 (JUnit 5, MockMvc, `FakeLlmClients`), React 19 + TypeScript, Vitest(노드 환경, `src/**/*.test.ts`만, 화면 테스트 없음).

**Spec:** 이 계획을 요청한 검토 결과 8건(별도 spec 문서 없음). 검토 결과와 코드가 다른 점은 아래 "검토 결과와 다른 점"에 적었다.

## 선행 조건 (시작 전에 확인)

- **백엔드 동시성 계획(`2026-11-27-backend-concurrency.md`), 프론트 회귀 계획(`2026-11-27-frontend-regressions.md`), 접근성 계획(`2026-11-27-accessibility.md`)이 머지된 뒤 시작한다.** 같은 파일(자료·퀴즈·평가 화면, 백엔드 퀴즈·자료 서비스)을 건드린다. 이 계획의 줄 번호는 main `756f6f7` 기준이다.
- 동시성 계획은 "큰 PDF 추출 동안 사용자 잠금을 오래 쥐는 문제"를 범위 밖으로 두고 이 계획에 넘겼다. 그래서 Task 9는 이 계획이 한다.
- 접근성 계획은 `MaterialConceptPanel`·`StudySummaryPanel`의 `AiWaiting` 자리(보내기 카드 안)를 알림 부품으로 바꾼다. Task 8은 그 결과 마크업을 그대로 카드로 옮긴다.
- 각 PR을 시작할 때 `git log --oneline -20 -- <이 PR의 파일들>`로 두 계획이 이 계획의 줄 번호·구조를 바꿨는지 보고, 바뀌었으면 Task의 "Files" 줄 번호를 다시 찾는다. **특히 Task 8(보내기 카드)은 접근성 계획이 바꾼 대기·완료 알림을 빠뜨리지 않는다.**
- 위키 목차(`docs/wiki/README.md`)를 먼저 훑는다. 특히 `docs/wiki/backend.md` "반복문을 공용 도우미로 합쳤더니 개념 뽑기의 시간 초과 동작이 바뀌었다": **합치기 전에 원본들을 줄 단위로 비교해 `break`/`continue`·오류 코드·문구 차이를 표로 적고, 다르면 옵션으로 남긴다.** Task 3·6·8·9·10·13에 그 표를 넣었다.

## Global Constraints

- **동작을 바꾸지 않는다**(PR 10 제외). HTTP 상태·`message`·`data.reason`·문구 글자 하나도 바꾸지 않는다. API 응답 형식·DTO·프론트 타입·DB 마이그레이션 변경 없음.
- 각 Task는 먼저 지금 동작을 고정하는 테스트가 있는지 보고, 없으면 추가해 **지금 코드에서 통과**시킨 뒤 옮긴다. 옮긴 뒤 같은 테스트가 그대로 통과해야 한다.
- 브랜치 `chore/<범위>`(루트 CLAUDE.md의 4가지 접두사 안에서), 커밋 `refactor: <한국어 설명>`(동작을 바꾸는 PR 10은 `fix:`). 한 PR에 한 가지만.
- 백엔드 확인: `cd backend && ./gradlew test`. PR 7(Task 9)은 `./gradlew pgTest`도. ArchUnit(`LlmClient.complete` 직접 호출 금지, 컨트롤러 Entity 응답 금지)을 지킨다.
- 프론트 확인: `cd frontend && npm run lint && npm run format:check && npm test && npm run build`. 컴포넌트 파일은 컴포넌트만 내보낸다(`react-refresh/only-export-components`, 상수는 같은 폴더 소문자 `.ts`). 새 공통 부품은 `components/common/index.ts` 배럴로 내보낸다. 색 코드·`dark:` 직접 금지, 토큰만.
- 화면 PR은 머지 전에 PR 미리보기 체험 모드로 확인한다. 화면이 조금이라도 바뀌는 PR(PR 9)은 시안 조정처럼 **화면 확인 뒤 한 번에 커밋**한다.
- 제출 12/11(금) 23:59. 제출 직전 큰 변경을 머지하지 않으므로 **권장: 12/8까지 머지하지 못한 PR은 머지하지 않는다**(열린 질문 1).
- 문서·주석은 한국어, 식별자는 영어. 문서에 날짜·PR 번호를 쓰지 않는다.

## Review Focus

- 요약 재사용은 전송 설정·동의·한도 확인보다 먼저다: 저장한 요약은 한도를 다 써도 다시 보여 준다. `MaterialAiGate`를 재사용 판단 **뒤에** 불러야 한다. → Task 3 Step 1 테스트(기존 429 테스트 + 순서 테스트), Task 9 Step 1.
- 개념 설명은 "없는 개념 404"가 "확인 체크 400"보다 먼저다(기존 `학습용_설명은_없는_개념_404_확인_없음_400_설명이_비면_502`). 게이트를 개념 조회 뒤에 부른다. → Task 4 Step 3 확인 항목.
- 추출을 잠금 밖으로 옮긴 뒤, 그 사이 다른 요청이 같은 자료·목적의 요약을 시작했으면 409, 끝냈으면 재사용이어야 한다(두 번 만들지 않음). → Task 9 Step 1 테스트(진행 중 행을 미리 넣어 409).
- 브라우저 저장소를 쓸 수 없는 체험(사생활 보호 모드·노드 테스트)에서도 목업 데이터가 이번 화면 동안 유지되고 앱이 깨지지 않는다. → Task 10 테스트 "저장소가 예외를 던지면".
- 운영·체험은 `materialContentEnabled: false`라 자료 상세의 개념·요약 카드에 체크 칸 대신 막힌 이유 문구만 보인다. 이 문구가 그대로인지, 체크 칸 분기는 로컬에서 따로 본다. → Task 5 단위 테스트, Task 8 Step 2.

---

## 검토 결과와 다른 점 (코드를 읽어 확인)

- **2번 "막힌 이유 4벌":** 같은 계산은 3벌이다. `AssessmentPage.tsx:160-168`은 본문 전송 조건(전송 설정·형식)을 보지 않고 AI 연결·동의·과목 연결만 본다. 그래서 함수를 둘로 나눈다(전송 조건 / AI 퀴즈 조건). 평가 화면은 AI 퀴즈 조건 함수만 쓴다.
- **7번 "체크박스 23곳(8파일)":** `type="checkbox"`는 **14곳(8파일)**이다. 그중 같은 모양(`mt-1|mt-0.5 size-4 accent-primary` + `gap-3 text-heading` 라벨)은 8곳(평가 2, 퀴즈 만들기 3, 개념 패널 2, 요약 1)이다. 회원가입 3곳은 글자 크기를 물려받고(`text-body` 없음) 로그인 흐름이라, 대화 가져오기 2곳·자료 올리기 1곳은 `size-4 accent-primary`가 없어 바꾸면 모양이 달라져 뺐다(열린 질문 4).
- **7번 "누름 토글 알약 3곳":** `aria-pressed` 알약은 퀴즈 만들기 2곳(개념·인지 수준, 클래스 같음)이고, 세 번째인 요약 목적 고르기(`StudySummaryPanel.tsx:236-262`)는 `role="radio"`에 `font-semibold`·`border-line`이라 클래스가 다르다. 합치면 요약 목적 버튼 모양이 조금 바뀐다(PR 9에서 화면 확인).
- **7번 "글자 링크 18곳":** 맞다. 다만 13곳은 `font-semibold`, 5곳(로그인·카카오 가입·연결·대기 오류·메인 처리방침 안내)은 `font-medium`이라 상수는 `font-semibold` 13곳에만 쓴다(열린 질문 5).
- **7번 "`role="alert"` 오류 문구 8곳":** `ErrorText`와 같은 마크업(`<p role="alert" className="text-body text-danger">`)은 8곳(ConceptFeedbackBox 1, 평가 1, 로그인 1, Turnstile 1, 카카오 대기 오류 1, 퀴즈 만들기 3)이다. `MaterialUploadPage.tsx:177`(움직임 `m.p`)·`QuizCreatePage.tsx:515`(`text-caption`)·대화 가져오기 화면들은 모양이 달라 뺐다.
- **8번 `MaterialConceptService.clip` → `Texts.clip`:** 그대로 바꾸면 동작이 바뀐다. 지금 `clip`은 빈 글을 null로 바꾸고 앞뒤 공백을 지운 뒤 자른다. `Texts.clip`은 둘 다 안 한다. 그래서 `StringUtils.hasText(text) ? Texts.clip(text.strip(), max) : null`로 바꾼다(Task 4).
- **1번 `QuizSpecResolver.java:310-340`:** 퀴즈는 본문을 못 쓰면 오류가 아니라 `Content.none(이유)`로 과목 개념만으로 만든다(개인정보·추출 실패 모두). 동의·모델·한도는 `QuizGenerationService.start`가 따로 확인한다. 그래서 퀴즈 쪽은 확인 문구 상수만 공유하고 흐름은 그대로 둔다.
- **1번 확인 순서는 두 서비스가 같지만 세부가 다르다:** 개념 뽑기는 본문 전체(`extract(material)` = `maxChars`)를 개인정보 검사하고, 요약은 `sourceMaxChars`로 읽어 고른 부분(`select`)만 검사한다. 하루 한도 값도 기능마다 다르다(`dailyGenerationLimit`·`conceptNoteDailyLimit`·`summaryDailyLimit`). 게이트는 이 차이를 인자로 받는다.
- **같이 발견:** 퀴즈 만들기도 `QuizSpecResolver.materialContent`가 사용자 행을 잠근 트랜잭션 안에서 본문을 추출한다(클래스 주석 "사용자 행을 잠근 QuizGenerationService의 트랜잭션 안에서 부른다"). 이 계획의 범위(개념·요약) 밖이라 열린 질문 3으로 남긴다.

## PR 목록과 순서

백엔드 줄(PR 2→4→7→10)과 프론트 줄(PR 1→3→5→6→8→9)은 서로 기다리지 않는다. 줄 안에서는 번호 순서.

| 순서 | PR(브랜치) | Task | 내용 | 위험(시연 영향) |
|---|---|---|---|---|
| 1 | `chore/frontend-dead-code` | 1, 2 | `filterByKeywords`·안 쓰는 토큰 지우기 | 매우 낮음: 화면 변화 없음. 대화 가져오기 키워드 규칙 테스트를 `assignToCourses`로 옮겨 지킨다 |
| 2 | `chore/material-ai-gate` | 3, 4 | `MaterialAiGate`로 확인 순서·문구 모으기, `clip` 정리 | 낮음: 운영은 자료 본문 전송이 꺼져 있어(`MATERIAL_CONTENT_ENABLED=false`) 첫 확인(403)만 탄다. 체험 모드는 서버를 부르지 않는다 |
| 3 | `chore/material-blocked-reason` | 5 | 막힌 이유 계산을 `utils/material.ts`로 | 낮음: 문구를 단위 테스트로 고정. 자료 상세·퀴즈 만들기·평가 화면 문구가 그대로인지 미리보기 확인 |
| 4 | `chore/model-fallback-tail` | 6 | 모델 차례 호출 뒤 502 꼬리를 `QuizModelFallback` 도우미로 | 낮음: 위키 교훈 표로 차이 확인, 전송이 꺼진 운영에서는 닿지 않음 |
| 5 | `chore/common-checkbox-errortext` | 7 | `Checkbox` 부품, `ErrorText message` | 중간: 로그인·Turnstile·퀴즈 만들기·평가 화면을 건드린다(마크업은 같음). 미리보기에서 로그인 화면·퀴즈 만들기 확인 |
| 6 | `chore/material-ai-send-card` | 8 | 개념 뽑기·요약 카드 `MaterialAiSendCard` | 낮음~중간: 자료 상세 두 탭. 체크 칸 분기는 체험에서 안 보여 로컬에서 확인 |
| 7 | `chore/material-text-outside-lock` | 9 | 본문 추출·해시를 잠금 밖으로 | 중간: 동시 요청 처리 순서가 바뀐다. 응답은 같게. 운영은 전송이 꺼져 있어 시연 영향 없음. `pgTest` 필수 |
| 8 | `chore/mock-storage` | 10 | 목업 localStorage 6벌 → `persisted<T>` | 중간: **체험 모드 전체가 목업에 기대므로 시연 영향이 가장 크다.** 미리보기에서 과목·자료·퀴즈 풀기·학습 기록이 새로고침 뒤에도 남는지, 체험 다시 시작하면 지워지는지 확인 |
| 9 | `chore/text-link-toggle-chip` | 11, 12 | `TEXT_LINK_CLASS`, `ToggleChip` | 낮음, 다만 요약 목적 버튼 모양이 조금 바뀐다(굵기·테두리). 화면 확인 뒤 커밋, 바뀌면 제출 자료 캡처 확인 |
| 10 | `fix/material-quote-check` | 13 | 개념 뽑기·설명의 인용 확인을 `SourceText` 규칙(공백 모두 무시)으로 | **동작 변경**: 띄어쓰기만 다른 인용을 새로 받는다. 운영은 전송이 꺼져 있어 시연 영향 없음. 통일 규칙 결정(열린 질문 2) 뒤에 시작 |

---

## PR 1 `chore/frontend-dead-code`

### Task 1: `filterByKeywords` 지우기 (규칙 테스트는 `assignToCourses`로 옮김)

**Files:**
- Modify: `frontend/src/utils/conversation/filter.ts:5-9`(`KeywordMatch`), `:83-102`(`filterByKeywords`)
- Modify: `frontend/src/utils/conversation/filter.test.ts:45-56`, `frontend/src/utils/conversation/parse.test.ts:2, 502-529`
- Test: `frontend/src/utils/conversation/assign.test.ts`

**Interfaces:**
- Consumes: `assignToCourses(conversations, courses): AssignResult`, `keywordHaystackOf`, `keywordTargets`, `matchKeywords` (그대로).
- Produces: 없음(`filterByKeywords`·`KeywordMatch` 삭제, 운영 코드에서 쓰는 곳 없음).

`filterByKeywords` 테스트가 지키는 규칙 5개와 지금 `assignToCourses` 테스트가 지키는지:

| 규칙 | `assign.test.ts` |
|---|---|
| 키워드가 든 대화만, 찾은 키워드를 등록 순서로 | 있음(35행) |
| 대소문자·앞뒤 공백 무시(`' rsa '`) | **없음** |
| 키워드가 비었거나 공백뿐(`['  ', '']`)이면 아무것도 | 빈 배열만 있음(69행), **공백뿐 없음** |
| 관련 없는 키워드면 고르지 않음 | 간접 |
| 제목과 메시지 경계도 낱말 경계(`'IP'`가 "shipping"에 안 걸림) | **없음** |

- [ ] **Step 1: 빠진 규칙 테스트를 `assign.test.ts`에 추가** (`conversation()`·`security` 도우미 재사용)

```ts
it('키워드의 대소문자와 앞뒤 공백을 무시한다', () => {
  const items = [conversation('a', 'RSA 키 길이는?')];
  expect(assignToCourses(items, [{ ...security, keywords: [' rsa '] }]).assigned).toHaveLength(1);
});
it('키워드가 공백뿐이면 아무것도 고르지 않는다', () => {
  const items = [conversation('a', '암호화')];
  expect(assignToCourses(items, [{ ...security, keywords: ['  ', ''] }]).assigned).toEqual([]);
});
it('제목과 메시지 사이도 낱말 경계로 본다', () => {
  const item = { ...conversation('a', 'shipping은 상관없어요'), title: '네트워크 질문 IP' };
  const course = { ...security, keywords: ['IP'] };
  expect(assignToCourses([item], [course]).assigned).toHaveLength(1);
  expect(assignToCourses([{ ...item, title: '배송 질문' }], [course]).assigned).toEqual([]);
});
```

- [ ] **Step 2: 지금 코드에서 통과 확인** — `cd frontend && npx vitest run src/utils/conversation/assign.test.ts` → PASS(특성 테스트라 처음부터 통과해야 한다. 실패하면 두 함수 규칙이 다른 것이니 멈추고 보고).
- [ ] **Step 3: 옮기기** — `parse.test.ts`의 `describe('filterByKeywords')` 4개는 같은 기대값으로 `assignToCourses(parsed, [{ id: 1, name: '운영체제', semester: '2026-2', keywords: [...] }])`를 쓰게 바꾼다(`matches[0].matchedKeywords` → `assigned[0].matches[0].matchedKeywords`, `[]` → `.assigned`가 `[]`). `filter.test.ts` 45-56은 `matchKeywords(keywordHaystackOf(c), keywordTargets(['IP']))`가 `['IP']`/`[]`인지로 바꾼다(이 파일은 서버 `TermMatchTest`와 짝인 규칙 표라 남긴다). 그다음 `filterByKeywords`·`KeywordMatch`를 지운다.
- [ ] **Step 4: 확인** — `npm test` PASS, `grep -rn "filterByKeywords\|KeywordMatch\b" src` 결과 없음.

### Task 2: 안 쓰는 디자인 토큰 지우기

**Files:**
- Modify: `frontend/src/index.css:18`(`--text-display`), `:37`·`:104`(`--color-primary-pill-hover` 밝은·어두운)
- Modify: `frontend/src/styles/theme-transition.css:42-46`(`@property`), `:206-207`(transition 목록)
- Modify: `frontend/src/utils/cn.ts:11`(`'display'` 빼기)
- Modify: `docs/frontend-guide.md:129`(`text-display`(40px) 언급 빼기)

- [ ] **Step 1: 쓰는 곳이 없는지 확인** — `grep -rn "text-display\|primary-pill-hover" frontend/src` 결과가 위 정의 줄뿐이어야 한다(`docs/wiki/frontend.md`의 교훈 기록은 그대로 둔다).
- [ ] **Step 2: 지우기** — 위 다섯 곳.
- [ ] **Step 3: 확인** — `npm run lint && npm run format:check && npm test && npm run build` PASS. 개발 서버에서 화면 모드 전환 한 번(다른 색 전환이 그대로인지).
- [ ] **Step 4: 커밋·PR** — `refactor: 쓰이지 않는 키워드 필터 함수와 디자인 토큰 지우기`.

---

## PR 2 `chore/material-ai-gate`

### Task 3: 보내기 전 확인 순서·문구 특성 테스트

**Files:**
- Test: `backend/src/test/java/com/msg/backend/domain/material/api/MaterialConceptApiTest.java`
- Test: `backend/src/test/java/com/msg/backend/domain/material/api/MaterialSummaryApiTest.java`

두 서비스의 확인 순서(지금 코드, 첫 오류를 돌려줌):

| 순서 | 개념 뽑기·설명 `MaterialConceptService.prepare` 282-320 | 요약 `MaterialSummaryService.prepare` 234-317 |
|---|---|---|
| 0 | 사용자 잠금, 자료 404 (설명은 그 전에 개념 404) | 사용자 잠금, 자료 404, **진행 중 409, 재사용이면 여기서 반환** |
| 1 | 전송 꺼짐 403 `DISABLED_MESSAGE` | 같음 |
| 2 | AI 퀴즈 동의 없음 403 (개념: "AI로 핵심 개념을 뽑으려면…", 설명도 같은 문구) | 403 "AI로 요약을 만들려면…" |
| 3 | 확인 체크 없음 400 (문구 같음) | 같음 |
| 4 | 모델 없음 503 "…핵심 개념을 뽑을 수 없어요." (설명도 같은 문구) | 503 "…요약을 만들 수 없어요." |
| 5 | 24시간 기능별 횟수(`dailyGenerationLimit`/`conceptNoteDailyLimit`) 또는 모델 호출 수 → 429 "오늘 AI 호출 한도를 다 써서 지금은 {핵심 개념을 뽑을/개념 설명을 만들} 수 없어요. 내일 다시 시도해 주세요." | `summaryDailyLimit` → 429 같은 틀("요약을 만들") |
| 6 | `aiCallQuota.ensureRemaining(userId, 1)` 429 | 같음 |
| 7 | `extract(material)`(= `maxChars`), OK 아니면 422 + `reason` | `extract(material, sourceMaxChars)`, 같은 문구 |
| 8 | **본문 전체** 개인정보 → 422 `PERSONAL_INFO` | 과목 조회 → `select` → 비면 422 `EMPTY` → **고른 부분** 개인정보 422 |
| 9 | 과목 조회(이름·키워드) | 해시, 진행 중 행 저장 |

- [ ] **Step 1: 빠진 특성 테스트 추가** (지금 코드에서 통과해야 한다)
  - 개념 `확인_순서는_동의_확인체크_모델_순이다`: 동의 철회 + `extract(material, false)` → 403, `message` "AI 퀴즈 처리 동의" 포함. 동의 다시 켜고 `fakeLlm.resetAll()` 뒤 `extract(material, false)` → 400, `extract(material, true)` → 503 `message` = `"연결된 AI 모델이 없어 지금은 핵심 개념을 뽑을 수 없어요."`.
  - 개념 `모델_호출_한도에_닿으면_뽑기와_설명이_기능별_문구로_429`: 먼저 `extract` 200, 그다음 `QuizSafetyApiTest:207`처럼 `quiz_ai_call`에 `attempts` 200짜리 행을 넣고 `extract` → 429 `message` = `"오늘 AI 호출 한도를 다 써서 지금은 핵심 개념을 뽑을 수 없어요. 내일 다시 시도해 주세요."`, `explain(material, "vlan", true)` → 429 같은 틀("개념 설명을 만들").
  - 개념 개인정보 422의 `message` = `"자료 본문에 이메일·전화번호 같은 개인정보가 있어 외부 AI로 보내지 않았어요."`(기존 테스트 `확인_체크_AI_퀴즈_처리_동의_개인정보_남의_자료를_검사한다`에 한 줄 추가).
  - 요약 `확인_체크는_모델_확인보다_먼저다`: 모델 없이(`setUp` 직후) `generate("exam_prep", false, false)` → 400.
  - 요약 `모델_호출_한도에_닿으면_429이고_요약_문구다`: `quiz_ai_call` 행으로 막은 뒤 `generate("work", true, false)` → 429 `message` = `"오늘 AI 호출 한도를 다 써서 지금은 요약을 만들 수 없어요. 내일 다시 시도해 주세요."`. 같은 테스트에서 이미 만든 `exam_prep` 요약은 200 `reused=true`(재사용이 게이트보다 먼저).
- [ ] **Step 2: 지금 코드에서 통과 확인** — `cd backend && ./gradlew test --tests "*MaterialConceptApiTest" --tests "*MaterialSummaryApiTest"` → PASS. 실패하면 표가 틀린 것이니 표를 고치고 보고.
- [ ] **Step 3: 커밋** — `test: 자료 본문 보내기 전 확인 순서와 문구를 테스트로 고정`.

### Task 4: `MaterialAiGate`로 모으기 + `clip` 정리

**Files:**
- Create: `backend/src/main/java/com/msg/backend/domain/material/application/MaterialAiGate.java`
- Modify: `backend/src/main/java/com/msg/backend/domain/material/application/MaterialConceptService.java:72-74`(상수), `:238,255-257`(`clip` 호출), `:260-267`(`clip` 삭제), `:282-330`(`prepare`·`unreadableMessage`)
- Modify: `backend/src/main/java/com/msg/backend/domain/material/application/MaterialSummaryService.java:108-112`(상수), `:251-296`
- Modify: `backend/src/main/java/com/msg/backend/domain/quiz/application/QuizSpecResolver.java:68`(`CONTENT_CONSENT_MESSAGE`)
- Test: `MaterialConceptApiTest`(clip 특성 테스트)

**Interfaces:**
- Produces (`MaterialAiGate`, `@Component`, 같은 패키지 `domain/material/application`):
  - `public static final String CONSENT_MESSAGE` = `"자료 본문을 외부 AI로 보내려면 확인에 체크해 주세요."`, `PERSONAL_INFO_MESSAGE` = `"자료 본문에 이메일·전화번호 같은 개인정보가 있어 외부 AI로 보내지 않았어요."`
  - `public record Messages(String quizConsent, String noModel, String limit)`
  - `public static String limitMessage(String action)` → `"오늘 AI 호출 한도를 다 써서 지금은 " + action + " 수 없어요. 내일 다시 시도해 주세요."`
  - `public List<LlmClient> checkBeforeSend(User user, boolean confirmed, String callPurpose, int dailyLimit, Messages messages)` — 표의 1~6을 그 순서·상태 코드로. 모델 순서는 `llmClients.configuredInOrder(quizProperties.generationProviders())`, 24시간 기준은 `LocalDateTime.now(clock).minusHours(24)`.
  - `public MaterialTextExtractor.Extracted read(Material material, int maxChars)` — OK가 아니면 422 + `Map.of("reason", status.name())`, 문구는 `unreadableMessage`.
  - `public void requireNoPersonalInfo(String text)` — 422 + `reason` `PERSONAL_INFO`.
  - `public static String unreadableMessage(MaterialTextExtractor.Status status)` — `MaterialConceptService.unreadableMessage`(322-330)를 그대로 옮김.
  - `public Course courseOrNull(Material material, Long userId)` — `courseId`가 null이거나 못 찾으면 null(두 서비스의 316-317·275-276).
- Consumes: `MaterialContentProperties`, `QuizProperties`, `LlmClients`, `QuizAiCallRepository`, `QuizAiUsage`, `AiCallQuota`, `MaterialTextExtractor`, `PersonalInfoDetector`, `CourseRepository`, `Clock`.

- [ ] **Step 1: `clip` 특성 테스트** — 기존 `개념_하나의_학습용_설명을_만들고_자료에_없는_연결_문장은_버린다`에 경우 하나 추가: 모델 답의 `materialContext`가 `"   "`, `example`이 `"  층별로 나눈다  "`, `importance`가 `"가".repeat(600)`이면 응답 `studyNote.materialContext` null, `example` `"층별로 나눈다"`, `importance` `"가".repeat(499) + "…"`. 지금 코드에서 PASS 확인.
- [ ] **Step 2: 게이트 만들기** — 위 Interfaces대로. 본문은 지금 두 `prepare`의 해당 줄을 옮긴다(새 규칙 없음).
- [ ] **Step 3: 개념 서비스에 연결** — `prepare`는 잠금·자료 조회 뒤 `gate.checkBeforeSend(user, confirmed, purpose, dailyLimit, new Messages(QUIZ_CONSENT_MESSAGE, NO_MODEL_MESSAGE, MaterialAiGate.limitMessage(action)))` → `gate.read(material, contentProperties.maxChars())` → `gate.requireNoPersonalInfo(text)` → `gate.courseOrNull`. `extract`·`explain`이 넘기는 `action`("핵심 개념을 뽑을"/"개념 설명을 만들")은 그대로. `CONSENT_MESSAGE`는 지우고 게이트 상수를 쓴다. `unreadableMessage`는 게이트로 옮긴다. `clip`은 지우고 호출 4곳을 `StringUtils.hasText(t) ? Texts.clip(t.strip(), max) : null`로 바꾼다(같은 동작, 위 "다른 점" 참고. 한 곳에 private 도우미 `noteField(String, int)`로 둬도 된다). 생성자에서 안 쓰게 된 의존성은 뺀다.
- [ ] **Step 4: 요약 서비스에 연결** — 진행 중·재사용 판단(240-250) **뒤에** `gate.checkBeforeSend(..., QuizAiCall.SUMMARY, contentProperties.summaryDailyLimit(), new Messages(QUIZ_CONSENT_MESSAGE, NO_MODEL_MESSAGE, LIMIT_MESSAGE))`. `LIMIT_MESSAGE`는 `MaterialAiGate.limitMessage("요약을 만들")`로 정의(글자 같음). `gate.read(material, contentProperties.sourceMaxChars())`, 과목·`select`·`EMPTY` 검사는 그대로, 고른 부분에 `gate.requireNoPersonalInfo(selection.text())`. `MaterialConceptService.unreadableMessage` 호출 2곳은 `MaterialAiGate.unreadableMessage`로.
- [ ] **Step 5: 퀴즈** — `QuizSpecResolver.CONTENT_CONSENT_MESSAGE`를 지우고 325행에서 `MaterialAiGate.CONSENT_MESSAGE`. 흐름(`Content.none`)은 그대로.
- [ ] **Step 6: 확인** — `./gradlew test` 전체 PASS(Task 3 테스트 포함, 고치지 않고). `docs/backend-guide.md:55`에 "보내기 전 확인은 `MaterialAiGate`" 한 마디 덧붙인다(같은 내용 두 곳에 쓰지 않게 6절에 이미 순서가 있으면 그 문단을 고친다).
- [ ] **Step 7: 커밋·PR** — `refactor: 자료 본문 보내기 전 확인을 MaterialAiGate 하나로 모으기`. PR 본문에 Task 3 표를 붙인다.

---

## PR 3 `chore/material-blocked-reason`

### Task 5: 막힌 이유 계산을 `utils/material.ts`로

**Files:**
- Modify: `frontend/src/utils/material.ts`
- Test: `frontend/src/utils/material.test.ts`
- Modify: `frontend/src/pages/materials/components/MaterialConceptPanel.tsx:270-282`(`blockedReasonOf` 삭제), `:337-341`
- Modify: `frontend/src/pages/materials/components/StudySummaryPanel.tsx:221-229`
- Modify: `frontend/src/pages/quizzes/QuizCreatePage.tsx:199-205`
- Modify: `frontend/src/pages/assessments/AssessmentPage.tsx:160-168`

**Interfaces:**
- Produces (`@/utils` 배럴로 이미 내보내짐):
  - `export const UNREADABLE_FORMAT_MESSAGE = '이 형식은 본문을 읽지 못해요. TXT·MD·PPTX·글이 들어 있는 PDF만 읽어요.'`
  - `materialContentBlockedReason(ai: Pick<QuizAiStatus, 'materialContentEnabled'>, fileName: string): string | null` — 꺼짐 → `CONTENT_DISABLED_MESSAGE`, `!isReadable` → `UNREADABLE_FORMAT_MESSAGE`, 아니면 null.
  - `aiQuizBlockedReason(ai: Pick<QuizAiStatus, 'available' | 'message'>, aiQuizConsent: boolean | undefined, action: string): string | null` — `!available` → `ai.message`, `aiQuizConsent === false` → `` `AI로 ${action}려면 AI 퀴즈 처리 동의가 필요해요. 마이페이지에서 동의할 수 있어요.` ``, 아니면 null.

화면별 지금 규칙(바꾸지 않는다):

| 화면 | 계산 |
|---|---|
| 개념 패널 | `materialContentBlockedReason ?? aiQuizBlockedReason(ai, me?.aiQuizConsent, '개념을 뽑거나 설명을 만들')` |
| 요약 패널 | `materialContentBlockedReason ?? aiQuizBlockedReason(ai, me?.aiQuizConsent, '요약을 만들')` |
| 퀴즈 만들기(본문) | `material ? materialContentBlockedReason(ai, material.fileName) : null` (변수 이름 `contentBlockedReason` 그대로) |
| 평가 | `mockData ? null : aiQuizBlockedReason(ai, me?.aiQuizConsent, '평가를 만들') ?? (material.courseId === null ? '과목에 연결된 자료로만 평가를 만들 수 있어요.' : null)` — `me && !me.aiQuizConsent`는 `aiQuizConsent: boolean`이라 `me?.aiQuizConsent === false`와 같다 |

- [ ] **Step 1: 실패하는 테스트** (`material.test.ts`, 4화면의 지금 문구를 그대로 기대값으로)

```ts
const on = { available: true, message: '', materialContentEnabled: true };
describe('materialContentBlockedReason', () => {
  it('전송이 꺼져 있으면 꺼짐 안내가 형식보다 먼저다', () => {
    expect(materialContentBlockedReason({ materialContentEnabled: false }, 'a.exe')).toBe(CONTENT_DISABLED_MESSAGE);
  });
  it('읽지 못하는 형식이면 형식 안내, 아니면 null', () => {
    expect(materialContentBlockedReason(on, 'a.exe')).toBe('이 형식은 본문을 읽지 못해요. TXT·MD·PPTX·글이 들어 있는 PDF만 읽어요.');
    expect(materialContentBlockedReason(on, '노트.PDF')).toBeNull();
  });
});
describe('aiQuizBlockedReason', () => {
  it('AI가 없으면 서버 문구가 동의보다 먼저다', () => {
    expect(aiQuizBlockedReason({ available: false, message: '연결된 모델이 없어요' }, false, '요약을 만들')).toBe('연결된 모델이 없어요');
  });
  it('동의가 false일 때만 막고 기능 이름을 넣는다', () => {
    expect(aiQuizBlockedReason(on, false, '개념을 뽑거나 설명을 만들')).toBe('AI로 개념을 뽑거나 설명을 만들려면 AI 퀴즈 처리 동의가 필요해요. 마이페이지에서 동의할 수 있어요.');
    expect(aiQuizBlockedReason(on, false, '요약을 만들')).toBe('AI로 요약을 만들려면 AI 퀴즈 처리 동의가 필요해요. 마이페이지에서 동의할 수 있어요.');
    expect(aiQuizBlockedReason(on, false, '평가를 만들')).toBe('AI로 평가를 만들려면 AI 퀴즈 처리 동의가 필요해요. 마이페이지에서 동의할 수 있어요.');
    expect(aiQuizBlockedReason(on, undefined, '요약을 만들')).toBeNull();
    expect(aiQuizBlockedReason(on, true, '요약을 만들')).toBeNull();
  });
});
```

- [ ] **Step 2: 실패 확인** — `npx vitest run src/utils/material.test.ts` → FAIL(함수 없음).
- [ ] **Step 3: 구현·연결** — 위 Interfaces와 표대로. 네 화면의 문구 문자열 리터럴은 남지 않아야 한다(`grep -rn "본문을 읽지 못해요. TXT" src/pages` 결과 없음).
- [ ] **Step 4: 확인** — 전체 프론트 검사 PASS. 미리보기 체험 모드: 자료 상세 핵심 개념·요약 탭에 `CONTENT_DISABLED_MESSAGE`, 퀴즈 만들기에서 자료를 고르면 같은 안내, 평가 화면 "지금 상태"가 전과 같은지.
- [ ] **Step 5: 커밋·PR** — `refactor: 자료 본문·AI 퀴즈 막힌 이유 계산을 utils/material.ts로 모으기`.

---

## PR 4 `chore/model-fallback-tail`

### Task 6: 모델 차례 호출 뒤 502 꼬리를 도우미로

**Files:**
- Modify: `backend/src/main/java/com/msg/backend/domain/quiz/application/QuizModelFallback.java`
- Modify: `MaterialConceptService.java:156-170`(`extract`), `:210-224`(`explain`)
- Modify: `MaterialSummaryService.java:201-222`(`generate`)
- Test: `MaterialConceptApiTest`, `MaterialSummaryApiTest`

세 꼬리 비교(위키 교훈):

| | 개념 뽑기 | 개념 설명 | 요약 |
|---|---|---|---|
| 마감 | 호출 직전 `now + generationDeadline` | 같음 | `prepare`에서 계산한 `prepared.deadline()` |
| `stopOnTimeout` | false | false | false |
| 검사 실패 로그 | warn | warn | warn |
| 결과 없음 | 502 `anyResponse ? NO_VALID_MESSAGE : MODEL_FAILED_MESSAGE` | 502 `NOTE_INVALID_MESSAGE : NOTE_FAILED_MESSAGE` | 502 `INVALID_MESSAGE : FAILED_MESSAGE` |
| 결과 있음 | 트랜잭션 `save` | 트랜잭션 `saveNote` | 트랜잭션 `save`, null이면 504, `finally finishIfRunning` |

같은 부분은 "`run` 뒤 비었으면 502(응답 유무로 문구 고르기)"뿐이다. 마감·`Call`·저장·504·`finally`는 부르는 쪽에 남긴다. 퀴즈 만들기(`QuizGenerationService`, `stopOnTimeout=true`, 실패 기록이 다름)는 건드리지 않는다.

**Interfaces:**
- Produces: `public <T> Success<T> runOrBadGateway(List<LlmClient> clients, Call call, Function<String, Optional<T>> parse, Tracker tracker, String invalidMessage, String failedMessage)` — `run(...)`을 부르고 비었으면 `BusinessException(HttpStatus.BAD_GATEWAY, tracker.anyResponse() ? invalidMessage : failedMessage)`. `call`은 받은 그대로 넘긴다(`stopOnTimeout`을 도우미가 정하지 않는다).

- [ ] **Step 1: 특성 테스트** (지금 코드에서 PASS)
  - 개념 `첫_모델의_답이_형식에_맞지_않으면_다음_모델로_넘어간다`: GPT는 `"{}"`, CLAUDE는 `CONCEPTS` 답 → 200, `quiz_ai_call`에 `purpose='CONCEPTS'`이고 `outcome='INVALID_OUTPUT'` 1행(gpt), `SUCCEEDED` 1행(provider `claude`).
  - 개념 `모델이_모두_응답하지_않으면_502_응답_실패_문구다`: GPT를 `respond(r -> { throw new LlmException(LlmException.Code.AUTH_FAILED, "401"); })` → 502 `message` = `MODEL_FAILED_MESSAGE` 글자. 기존 `지어낸_개념만_오면_저장하지_않고_502`에 `message` = `NO_VALID_MESSAGE` 글자 확인 추가.
  - 설명: 기존 `설명이_비면_502`에 `message` = `NOTE_INVALID_MESSAGE` 글자 확인 추가.
  - 요약: 기존 `인용을_하나도_확인하지_못하면_저장하지_않고_502이고_이전_요약은_남는다`에 `message` = `INVALID_MESSAGE` 글자 확인 추가.
- [ ] **Step 2: 통과 확인** — `./gradlew test --tests "*MaterialConceptApiTest" --tests "*MaterialSummaryApiTest"` PASS.
- [ ] **Step 3: 도우미 추가·세 곳 연결** — 위 표에서 같은 부분만 바꾼다. 요약의 `try/finally`·504는 그대로.
- [ ] **Step 4: 확인** — `./gradlew test` 전체 PASS. PR 본문에 위 표를 붙인다.
- [ ] **Step 5: 커밋** — `refactor: 자료 개념·설명·요약의 모델 실패 502 처리를 QuizModelFallback 도우미로`.

---

## PR 5 `chore/common-checkbox-errortext`

### Task 7: `Checkbox` 부품과 `ErrorText message`

**Files:**
- Create: `frontend/src/components/common/Checkbox.tsx`
- Modify: `frontend/src/components/common/ErrorText.tsx`, `frontend/src/components/common/index.ts`
- Modify (`Checkbox`): `pages/assessments/AssessmentPage.tsx:253-290`(2곳), `pages/quizzes/QuizCreatePage.tsx:563-620`(3곳), `pages/materials/components/MaterialConceptPanel.tsx:219-233`(개념 카드 설명, caption)
- Modify (`ErrorText`): `pages/analysis/components/ConceptFeedbackBox.tsx:147-151`, `pages/assessments/AssessmentPage.tsx:303-307`, `pages/auth/LoginPage.tsx:69-73`, `pages/auth/components/TurnstileWidget.tsx:109-112`, `pages/auth/components/SocialPendingError.tsx:9-13`, `pages/quizzes/QuizCreatePage.tsx:686-710`(3곳)
- Modify: `docs/frontend-guide.md` 1절(공통 부품 목록)

**Interfaces:**
- Produces:
  - `Checkbox({ checked, onChange, disabled?, size = 'body', children }: { checked: boolean; onChange: (checked: boolean) => void; disabled?: boolean; size?: 'body' | 'caption'; children: ReactNode })` — `<label className="flex items-start gap-3 text-heading {text-body|text-caption}"><input type="checkbox" className="{mt-1|mt-0.5} size-4 accent-primary" …/><span>{children}</span></label>`. 지금 8곳의 마크업과 같아야 한다(평가 첫 칸의 `onChange` 부수 동작은 부르는 쪽 함수에 그대로).
  - `ErrorText({ error, message }: { error?: Error | null; message?: string | null })` — 보일 글은 `error?.message ?? message`, 없으면 null. 마크업은 지금과 같다. 기존 22곳(`error=`)은 그대로 동작.
- 내보내기: `export { default as Checkbox } from './Checkbox';`

- [ ] **Step 1: 대상 확인** — `grep -rn -A6 'type="checkbox"' src/pages | grep className`로 8곳이 `mt-1 size-4 accent-primary`/`mt-0.5 size-4 accent-primary`이고 라벨이 `flex items-start gap-3 text-{body|caption} text-heading`인지 본다. 회원가입·대화 가져오기·자료 올리기 체크는 바꾸지 않는다.
- [ ] **Step 2: 부품 만들기·바꾸기** — 위 Interfaces대로. `MaterialConceptPanel.tsx:383`·`StudySummaryPanel.tsx:297`은 Task 8에서 카드와 함께 바꾸므로 여기서 건드리지 않는다.
- [ ] **Step 3: 문서** — `frontend-guide.md` 1절 부품 목록에 "확인 체크 `Checkbox`(`size="caption"`), 오류 문구 `ErrorText`(`error` 또는 `message`)"를 기존 문장에 끼워 넣는다.
- [ ] **Step 4: 확인** — 전체 프론트 검사 PASS. 미리보기에서 로그인 화면(카카오 오류 문구가 보이게 `?socialError` 흐름이 있으면 그것), 퀴즈 만들기(모의시험 체크, AI 연결 안내), 평가 화면 체크 칸을 스크린샷으로 전후 비교.
- [ ] **Step 5: 커밋·PR** — `refactor: 확인 체크 Checkbox 부품을 만들고 오류 문구를 ErrorText로 모으기`.

---

## PR 6 `chore/material-ai-send-card`

### Task 8: `MaterialAiSendCard`

**Files:**
- Create: `frontend/src/pages/materials/components/MaterialAiSendCard.tsx`
- Modify: `frontend/src/pages/materials/components/StudySummaryPanel.tsx:282-325`
- Modify: `frontend/src/pages/materials/components/MaterialConceptPanel.tsx:369-415`

**Interfaces:**
- Consumes: `Checkbox`(Task 7), `Card`·`Notice`·`ErrorText`·`AiWaiting`·`Button`, 막힌 이유(Task 5).
- Produces: `MaterialAiSendCard(props)` default export, props:
  `title: string; notice: ReactNode; blockedReason: string | null; confirmed: boolean; onConfirmedChange: (checked: boolean) => void; confirmLabel: ReactNode; error: Error | null; pending: boolean; pendingText: ReactNode; submitLabel: string; onSubmit: () => void; mockNote: ReactNode | null`.
  마크업은 두 카드의 지금 구조 그대로: `Card as="section" p-5 gap-3` → `h3` → `Notice icon={Lock} size="caption"` → 막힌 이유 `<p className="text-body text-ink-soft">` 또는 `Checkbox` → `ErrorText` → `pending && AiWaiting` → 버튼(`disabled={blockedReason !== null || !confirmed || pending}`) + `mockNote`.

두 카드에서 다른 값(호출하는 쪽이 넘김):

| | 요약 | 핵심 개념 |
|---|---|---|
| title | 결과 있으면 "요약 다시 만들기" | 개념 있으면 "핵심 개념 다시 뽑기" |
| notice | "자료가 짧으면 전체를…다시 써요." | "자료 본문 앞부분(최대 12,000자, PDF는 100쪽까지)을…바뀌어요." |
| confirmLabel | "이 자료의 본문을 외부 생성형 AI로…" | "이 자료의 본문 발췌를 외부 생성형 AI로…" |
| pendingText | "AI가 요약을 쓰고 서버가 원문 근거를 확인하는 중이에요. 최대 1분 40초 걸릴 수 있어요." | "AI가 개념을 찾는 중이에요. 최대 1분 40초 걸릴 수 있어요." |
| submitLabel / onSubmit | 결과·최신이면 "새로 만들기" `mutate(true)`, 아니면 "요약 만들기" `mutate(false)` | "핵심 개념 뽑기" `mutate()` |
| mockNote | "…예시 요약은 미리 써 둔 예시예요." | "…예시 개념의 설명은 미리 써 둔 예시예요." |

- [ ] **Step 1: 옮기기** — 문구는 복사해 붙여 글자 하나 바꾸지 않는다. 접근성 계획이 `AiWaiting` 자리를 알림 부품(대기·완료 문구, 표에는 "핵심 개념을 다 찾았어요."·"요약을 다 만들었어요.")으로 바꿨으면 그 부품과 완료 문구도 props(`doneText`)로 받아 그대로 옮긴다. 두 패널에서 안 쓰게 된 import를 지운다.
- [ ] **Step 2: 확인** — 전체 프론트 검사 PASS. 미리보기 체험 모드: 자료 상세 두 탭 카드가 전과 같은지(막힌 이유 분기). **체크 칸 분기는 체험에서 보이지 않으므로** 로컬에서 `mocks/quizzes.ts:698`의 `materialContentEnabled`를 잠깐 `true`로 바꿔(커밋하지 않음) 체크 → 버튼 켜짐 → 목업 결과까지 본 뒤 되돌린다.
- [ ] **Step 3: 커밋·PR** — `refactor: 개념 뽑기·요약의 AI로 본문 보내기 카드를 MaterialAiSendCard로`.

---

## PR 7 `chore/material-text-outside-lock`

### Task 9: 본문 추출·해시를 사용자 행 잠금 밖으로

**Files:**
- Modify: `backend/src/main/java/com/msg/backend/domain/material/application/MaterialConceptService.java`(`extract`·`explain`·`prepare`)
- Modify: `backend/src/main/java/com/msg/backend/domain/material/application/MaterialSummaryService.java`(`generate`·`prepare`, 해시 298)
- Modify: `MaterialAiGate.java`(Task 4) — 변경 없음, 부르는 위치만 바뀜
- Test: `MaterialSummaryApiTest`

지금은 사용자 행을 잠근 트랜잭션 안에서 큰 PDF 추출(`MaterialConceptService.java:306`, `MaterialSummaryService.java:269`)과 원본 SHA-256(`:298`)을 한다. 그동안 같은 사용자의 다른 요청(자료 올리기·퀴즈 만들기 등 `lockLoginUser`를 쓰는 모든 요청)이 기다린다.

설계(단계 나누기, 응답은 그대로):

| 단계 | 개념 뽑기·설명 | 요약 |
|---|---|---|
| 1. 트랜잭션(잠금) | 잠금, 자료 404(설명은 개념 404 먼저), `gate.checkBeforeSend`, 과목 이름·키워드 읽기 | 잠금, 자료 404, 진행 중 409·재사용 반환, `gate.checkBeforeSend`, 과목·이 자료 개념 이름(고르기 용어) 읽기 |
| 2. 트랜잭션 밖 | `gate.read` → `gate.requireNoPersonalInfo(text)` | `gate.read` → `select` → `EMPTY` 422 → `requireNoPersonalInfo(selection)` → 해시 |
| 3. 트랜잭션(잠금) | 없음(저장 단계 `save`가 이미 잠금·자료 404를 다시 본다) | 잠금, 자료 404, **진행 중 409·재사용을 다시 판단**, `gate.checkBeforeSend` 다시, 마감 계산, 진행 중 행 저장/`restart` |

- 단계 2의 `Material`은 단계 1에서 읽은 엔티티(트랜잭션이 끝나 분리된 상태)를 쓴다. `MaterialTextExtractor.extract`는 `getFileFormat()`·`getStorageKey()`만 읽는다.
- 그 사이 자료가 지워지면 개념은 단계 2에서 파일을 못 열어 422 `FAILED`("자료 파일을 읽지 못했어요…")가 될 수 있다(지금은 지울 때까지 기다렸다가 모델을 부르고 저장에서 404). 500이 아니면 받아들인다(PR 본문에 적기).

- [ ] **Step 0: 동시성 계획 머지 확인** — 동시성 계획 PR 1·2가 main에 있는지, 두 `prepare`의 줄 번호가 바뀌었는지 본다(그 계획은 이 부분을 바꾸지 않는다고 적었다).
- [ ] **Step 1: 특성 테스트** (지금 코드에서 PASS) — 요약 `진행_중인_요약이_있으면_409이고_마감이_지났으면_새로_만든다`: `material_summary`에 `(user_id, material_id, purpose='EXAM_PREP', status='RUNNING', deadline_at=지금+1분, updated_at=지금)`을 넣고 `generate("exam_prep", true, false)` → 409 `message` = `RUNNING_MESSAGE` 글자, 모델 요청 0. `deadline_at`을 지금-1분으로 바꾸면 200(V5 표의 NOT NULL 열을 채운다. 시각은 테스트의 `clock`).
- [ ] **Step 2: 통과 확인** — `./gradlew test --tests "*MaterialSummaryApiTest" --tests "*MaterialConceptApiTest"` PASS.
- [ ] **Step 3: 나누기** — 위 표대로. 요약 `Prepared`는 단계 3 끝에 만든다(`summaryId`·`deadline`). 단계 3의 재사용 판단은 단계 1과 같은 메서드로(복사하지 않는다).
- [ ] **Step 4: 확인** — `./gradlew test`와 `./gradlew pgTest` PASS(트랜잭션 경계는 H2와 PostgreSQL 잠금 동작이 다를 수 있다). 클래스 주석(`MaterialSummaryService` "동시 요청" 문단, `MaterialConceptService` 머리말)을 새 단계에 맞게 고친다.
- [ ] **Step 5: 커밋·PR** — `refactor: 자료 본문 추출과 해시를 사용자 행 잠금 밖에서 하기`. PR 본문에 단계 표와 "자료가 그 사이 지워지면 422" 차이를 적는다.

---

## PR 8 `chore/mock-storage`

### Task 10: 목업 localStorage 저장을 `persisted<T>` 하나로

**Files:**
- Create: `frontend/src/mocks/storage.ts`
- Test: `frontend/src/mocks/storage.test.ts`
- Modify: `frontend/src/mocks/courses.ts:34-56`, `notices.ts:41-60`, `materials.ts:179-200`, `study.ts:58-82`, `quizzes.ts:203-247`(결과·만든 퀴즈 2벌)

지금 6벌의 차이(그대로 지킨다):

| 파일·키 | 처음 값 | 저장된 값 되살리기 | 캐시 | 저장소 오류 때 |
|---|---|---|---|---|
| courses `msg-mock-courses` | `SAMPLE` | 그대로 | 처음 한 번만 읽음 | `SAMPLE`, 이후 메모리 |
| notices `msg-mock-notices` | `SAMPLE` | 그대로 | 한 번 | 같음 |
| materials `msg-mock-materials` | `SAMPLE` | `withStudyNotes(saved)` | 한 번 | 같음 |
| study `msg-mock-study` | `{ ...SAMPLE_RECORDS }` | `{ ...SAMPLE_RECORDS, ...saved }` | 한 번 | `{ ...SAMPLE_RECORDS }` |
| quizzes 결과 `msg-mock-quiz-results-v2` | `{}` | 그대로 | 한 번 | `{}` |
| quizzes 만든 퀴즈 `msg-mock-created-quizzes` | `[]` | 그대로 | **매번 읽음**(체험을 다시 시작해 지운 뒤 옛 목록이 남지 않게) | 마지막으로 저장한 메모리 값 |

**Interfaces:**
- Produces: `persisted<T>(key: string, options: { initial: () => T; revive?: (saved: T) => T; cache?: boolean }): { get(): T; set(next: T): void }` — `cache` 기본 true. `get`: 캐시가 있으면 그것, 아니면 `localStorage.getItem(key)`(try 안에서 `localStorage`를 처음 참조) → 있으면 `revive(JSON.parse)`, 없으면 `initial()`; 예외면 `memory ?? initial()`. `set`: `memory = next`(캐시면 캐시도), `setItem`은 예외를 삼킨다. 모듈을 불러오기만 해서는 저장소를 건드리지 않는다(`VITE_USE_MOCK=false` 앱 시작).
- 키 이름은 바꾸지 않는다(`useDemoStore.ts`가 `msg-mock-*`를 지운다).

- [ ] **Step 1: 실패하는 테스트** (`vi.stubGlobal('localStorage', …)`, `maskWords.test.ts`의 `memoryStorage` 모양, 테스트마다 `vi.unstubAllGlobals()`)
  - `저장된 값이 없으면 initial, 있으면 revive한 값을 준다`
  - `cache면 한 번만 읽는다` — `get()` 두 번, `getItem` 호출 1회.
  - `cache가 false면 매번 읽어 지운 뒤에는 initial로 돌아간다` — `set([1])` → `removeItem` → `get()`이 `[]`.
  - `불러오기만 해서는 저장소를 읽지 않는다` — `persisted(...)` 만든 직후 `getItem` 0회.
  - `저장소가 예외를 던지거나 JSON이 깨지면 initial이고 set한 값은 메모리에 남는다` — `getItem`이 throw하는 저장소에서 `get()` = initial, `set(x)` 뒤 `get()` = x(캐시 false여도).
- [ ] **Step 2: 실패 확인** — `npx vitest run src/mocks/storage.test.ts` FAIL.
- [ ] **Step 3: 구현·6곳 연결** — 위 표의 값을 `initial`·`revive`·`cache`로 넘긴다. 각 파일의 `getAll`·`setAll`·`setRecord`·`setResult`·`allQuizzes` 이름과 바깥 함수 시그니처는 그대로 두고 안쪽만 바꾼다.
- [ ] **Step 4: 확인** — 전체 프론트 검사 PASS(`mocks/quizzes.test.ts`·`study.test.ts`·`useDemoStore.test.ts` 그대로 통과).
- [ ] **Step 5: 미리보기 체험 확인** — 과목 추가 → 새로고침 뒤 남음, 자료 올리기·삭제, 퀴즈 만들기·풀기 결과가 새로고침 뒤 남음, 개념 "학습 완료" 기록 남음, 체험 끝내기·다시 시작하면 모두 처음 예시로. 사생활 보호 창에서도 화면이 깨지지 않음.
- [ ] **Step 6: 커밋·PR** — `refactor: 목업 데이터 브라우저 저장을 mocks/storage.ts 하나로`.

---

## PR 9 `chore/text-link-toggle-chip`

화면이 조금 바뀌는 PR이라 **화면 확인 뒤 한 번에 커밋**한다.

### Task 11: `TEXT_LINK_CLASS`

**Files:**
- Create: `frontend/src/components/common/textLinkClass.ts`(`fieldClass.ts`와 같은 모양)
- Modify: `components/common/index.ts`, 13곳 — `components/common/StartPaths.tsx:99,104`, `pages/assessments/AssessmentPage.tsx:95,314`, `pages/analysis/components/ConceptFeedbackBox.tsx:141`, `pages/analysis/components/AssessmentComparisonPanel.tsx:195,208,358,398`, `pages/analysis/components/MasteryEvidence.tsx:85`, `pages/curriculum/CurriculumPage.tsx:175,186`, `pages/quizzes/QuizCreatePage.tsx:291`(줄 번호는 PR 5·6 뒤 다시 확인)

**Interfaces:**
- Produces: `export const TEXT_LINK_CLASS = 'font-semibold text-primary underline-offset-4 hover:underline';` 배럴로 `export { TEXT_LINK_CLASS } from './textLinkClass';`. 다른 클래스가 붙은 곳은 `cn(TEXT_LINK_CLASS, '<나머지>')`.

- [ ] **Step 1: 바꾸기** — 13곳. `font-medium` 5곳은 그대로 둔다(열린 질문 5).
- [ ] **Step 2: 확인** — 전체 프론트 검사 PASS, `grep -rn "font-semibold text-primary underline-offset-4 hover:underline" src`가 `textLinkClass.ts` 하나.

### Task 12: `ToggleChip`

**Files:**
- Create: `frontend/src/components/common/ToggleChip.tsx`
- Modify: `components/common/index.ts`, `pages/quizzes/QuizCreatePage.tsx:380-404`(개념), `:478-505`(인지 수준), `pages/materials/components/StudySummaryPanel.tsx:236-262`(학습 목적, `role="radio"`)
- Modify: `docs/frontend-guide.md` 1절(목록에 `ToggleChip`·`TEXT_LINK_CLASS`)

**Interfaces:**
- Produces: `ToggleChip({ on, onClick, disabled?, title?, role?, children }: { on: boolean; onClick: () => void; disabled?: boolean; title?: string; role?: 'radio'; children: ReactNode })` — `<button type="button">`, `role="radio"`면 `role="radio" aria-checked={on}`, 아니면 `aria-pressed={on}`. 클래스는 퀴즈 만들기 것 그대로: `rounded-full border px-3 py-1.5 text-caption font-medium disabled:cursor-not-allowed disabled:opacity-50` + `on && !disabled ? 'border-primary bg-primary-soft text-primary' : 'border-line-strong text-ink-soft hover:bg-surface-muted'`. 인지 수준 알약 안의 설명 `<span>`은 `children`으로.

- [ ] **Step 1: 바꾸기** — 퀴즈 만들기 2곳은 모양이 같다. 요약 목적 3개는 `font-semibold`→`font-medium`, 꺼진 테두리 `border-line`→`border-line-strong`으로 **바뀐다**.
- [ ] **Step 2: 화면 확인** — 미리보기 체험: 퀴즈 만들기 개념·인지 수준 알약(누름·해제·막힘), 자료 상세 요약 목적 고르기(밝은·어두운 모드) 스크린샷 전후 비교를 사용자에게 보여 확인받는다. 요약 목적 모양이 거절되면 그 한 곳은 되돌리고 퀴즈 만들기 2곳만 쓴다.
- [ ] **Step 3: 확인·커밋** — 전체 프론트 검사 PASS 뒤 `refactor: 글자 링크 클래스와 누름 알약 ToggleChip을 공통 부품으로`. 제출 자료(노션·영상)에 요약 목적 버튼이 크게 보이면 PR 본문에 적는다.

---

## PR 10 `fix/material-quote-check` (동작 변경)

### Task 13: 개념 뽑기·설명의 인용 확인을 `SourceText` 규칙으로

**결정이 필요하다(열린 질문 2).** 이 계획은 `SourceText` 규칙(공백을 모두 지우고 비교)으로 통일하자고 제안한다.

지금 두 벌:

| | 개념 뽑기·설명 (`MaterialConceptService`) | 요약·퀴즈 (`SourceText.check`) |
|---|---|---|
| 비교 | `normalize` = 소문자 + 공백 묶음을 한 칸으로 + 앞뒤 공백 제거(383-385) | `compact` = 소문자 + 공백 모두 제거(240-242) |
| 위치 | `MARKER` `[p. N]`·`[슬라이드 N]`(93, 373-380) | `COMPACT_MARKER` + `[문단 N]`(38, 281-294) |
| 길이 | 4자 이상, `QUOTE_MAX_LENGTH`(300) 넘으면 거절 | 10자 이상, 300자 넘으면 앞 300자만 |
| 일부 일치 | 없음 | 낱말 60% 이상이면 PARTIAL |

근거(어느 쪽이 정상 인용을 더 받는지): `compact(x)`는 `normalize(x)`에서 공백만 더 지운 것이다. 공백을 지워도 부분 문자열은 부분 문자열로 남으므로, **지금 개념 쪽이 받는 인용은 `compact`도 모두 받는다.** 더 받는 것은 띄어쓰기만 다른 인용이다: 모델이 띄어쓰기를 고친 "공개 키로"↔자료 "공개키로", PDF 줄바꿈이 낱말 중간에 들어간 "암호\n화"↔"암호화"(지금은 "암호 화"로 바뀌어 거절). 글자 순서가 같아야 하므로 지어낸 문장을 받지는 않는다(형식 검증이라는 원칙 그대로).

바꾸는 범위: **인용 비교와 위치 찾기만.** 길이 기준(4자·300자 거절)과 "일부 일치 없음"은 개념 기능의 규칙이라 그대로 둔다. 개념 이름 확인(`haystack.contains(normalize(name))`)도 그대로 둔다(열린 질문 2-나).

**Files:**
- Modify: `backend/src/main/java/com/msg/backend/domain/material/application/SourceText.java`(새 메서드, `check`가 그것을 씀)
- Modify: `MaterialConceptService.java:241-252`(`parseNote`), `:353-366`(`verify`), `:372-380`(`location` 삭제), `:93`(`MARKER` 삭제)
- Test: `SourceTextTest.java`, `MaterialConceptApiTest.java`

**Interfaces:**
- Produces: `public record Located(String location)`(위치 표시가 없으면 `location` null)와 `public static Optional<Located> locate(String text, String quote)` — `compact(quote)`가 비었으면 empty, `compact(text).indexOf(...)`가 0 이상이면 `compactLocation`으로 위치. `check`의 VERIFIED 경로도 이것을 쓴다(동작 같음, `SourceTextTest` 그대로 통과).

- [ ] **Step 1: 실패하는 테스트**
  - `SourceTextTest.locate는_띄어쓰기_차이를_무시하고_앞의_마지막_위치를_준다`: `"[p. 2]\n공개키로 암호화하면\n개인키로만 풀 수 있다"`에서 `"공개 키로 암호화하면 개인 키로만"` → `Located("p. 2")`, 위치 표시 없는 글 → `location` null, 없는 문장 → empty.
  - `MaterialConceptApiTest.띄어쓰기만_다른_인용도_자료_문장으로_받는다`: 자료 `"공개키로 암호화하면 개인키로만 풀 수 있다"`, 모델 `{"concepts":[{"name":"공개키","description":"d","quote":"공개 키로 암호화하면 개인 키로만 풀 수 있다"}]}` → 200, `$.data[0].evidenceQuote`는 모델이 쓴 문장 그대로(지금 규칙과 같은 저장 방식). 지금 코드에서는 502라 FAIL.
  - 같은 파일 `설명의_연결_문장도_띄어쓰기_차이를_무시한다`: `contextQuote`가 자료 문장과 띄어쓰기만 다르면 `studyNote.contextQuote`가 null이 아님.
- [ ] **Step 2: 실패 확인** — `./gradlew test --tests "*SourceTextTest" --tests "*MaterialConceptApiTest"` → 새 테스트 FAIL.
- [ ] **Step 3: 구현** — `verify`·`parseNote`의 인용 찾기를 `SourceText.locate(prepared.text(), quote)`로, 위치는 `Located.location()`. 길이 검사는 지금 자리 그대로. `normalize`는 이름 확인에만 남는다.
- [ ] **Step 4: 확인** — `./gradlew test` 전체 PASS(기존 PDF "p. 2"·슬라이드 "슬라이드 3" 위치 테스트 포함). 바뀐 점: 원문에 `[문단 N]`이라는 글이 그대로 들어 있는 TXT·MD는 이제 그 값이 위치로 잡힐 수 있다(본문 추출은 `[문단]` 표시를 붙이지 않으므로 사용자가 직접 쓴 경우만).
- [ ] **Step 5: 문서·위키·커밋** — `backend/CLAUDE.md` "자료 개념·인용은 본문에 실제로 있는 것만" 줄 옆에 "인용 대조는 `SourceText`(공백·대소문자 무시) 하나"를 한 마디로. 위키에 교훈 한 건("같은 인용이 기능마다 다르게 판정됐다"). 커밋 `fix: 자료 개념 인용 확인을 요약·퀴즈와 같은 규칙(공백 무시)으로 맞추기`.

---

## 열린 질문

1. **머지 마감:** 제출(12/11) 전 큰 변경 금지에 맞춰 "12/8까지 머지 못 한 PR은 머지하지 않는다"로 할지, 다른 날짜로 할지.
2. **인용 통일 규칙(PR 10):** (가) 제안대로 `SourceText` 규칙(공백 모두 무시)으로 통일할지, 지금처럼 두 벌로 둘지. (나) 개념 **이름** 확인도 바꿀지: 지금은 부분 문자열이라 "NAT"가 "signature" 안에서도 맞는다. 위키 교훈("영문 개념 이름을 부분 문자열로 찾으면…")대로 `TermMatch` 낱말 경계로 바꾸면 더 엄격해진다. 이 계획에는 넣지 않았다.
3. **퀴즈 만들기의 잠금 안 추출:** `QuizSpecResolver.materialContent`도 사용자 행 잠금 안에서 본문을 읽는다. 동시성 계획도 다루지 않는다. 이 계획 PR 7에 넣을지(퀴즈는 추출 실패를 오류가 아니라 `Content.none`으로 다뤄 단계 나누기가 다르다), 따로 둘지.
4. **나머지 체크박스 6곳(회원가입 3, 대화 가져오기 2, 자료 올리기 1):** 모양이 달라(글자 크기 상속, `accent-primary` 없음) 이번에 뺐다. `Checkbox`로 맞추면 크기·색이 바뀐다. 맞출지.
5. **글자 링크 `font-medium` 5곳(로그인·카카오·메인 처리방침 안내):** `font-semibold`로 맞출지, 그대로 둘지.
