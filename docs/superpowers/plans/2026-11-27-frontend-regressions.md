# 프론트 회귀·규칙 위반 고치기 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 코드 검토에서 확인된 프론트 회귀 6건(대시보드 모델 표식 글자, 확인 창 id 중복, 체험 중 세션 표시 삭제, `outline-none`, 자료 목록 줄바꿈, 템플릿 문자열 클래스)을 한 PR로 고친다.

**Architecture:** 화면 구조와 API는 바꾸지 않는다. 모델 표식은 칸 모양(디자인 4단계 의도)을 그대로 두고 칸 아래에 짧은 판단 글자를 보이게 하며, 글자를 고르는 규칙은 `utils/analysis.ts`의 순수 함수로 빼서 테스트한다. 세션 복원은 "메모리만 익명으로" 두는 스토어 함수를 따로 만들어 공용 표시(`msg-has-session`)를 지키고, 나머지는 클래스·id 수준의 작은 수정이다.

**Tech Stack:** React 19 + TypeScript 6, Tailwind CSS 4.3, Zustand 5, Vitest (DOM 환경 없음: 컴포넌트 렌더 테스트는 쓰지 않는다).

**Spec:** 별도 설계 문서 없음. 이 계획의 각 Task "문제" 줄이 검토 결과 원문이다. 규칙 근거는 루트 `CLAUDE.md`, `frontend/CLAUDE.md`("학습 대시보드", "색 규칙", "로그인·인증"), `docs/wiki/frontend.md`.

## Global Constraints

- 브랜치 `fix/frontend-regressions`(main 756f6f7에서). 한 PR, 한 커밋. 커밋 예: `fix: 대시보드 모델 표식에 판단 글자를 보이고 확인 창 id·세션 표시·포커스 회귀 고치기`.
- 화면을 확인받으며 고치는 작업이라 **Task마다 커밋하지 않는다.** 작업 트리에만 반영하고 Task 7 화면 확인 뒤 한 번에 커밋한다(아래 Task의 "커밋" 단계는 없다).
- 작업 후 `cd frontend && npm run lint && npm run format:check && npm test && npm run build`가 모두 통과해야 한다(형식이 틀리면 `npm run format`).
- 테스트는 화면이 아니라 틀리면 안 되는 로직에만 쓴다(판단 글자 규칙, 세션 표시). 화면은 미리보기로 확인한다.
- 색 코드 직접 금지(`text-[#…]`, `bg-white`, `text-white`), `dark:` 클래스 금지. `surface`·`ink`·`line` 계열과 상태 토큰만.
- 아이콘에는 늘 글자를 붙인다(색·아이콘만으로 뜻을 전하지 않는다). 연결 안 된 모델은 숨기지 않고 "연결 안 됨"으로 보여 준다.
- 블루(`primary`) = 코드가 계산한 값과 일반 UI, 보라(`ai`) = AI가 만든 글. 모델 표식 칸 색은 지금 상태별 토큰(`strength`·`primary`·`weakness`·점선 회색) 그대로.
- 모델 비교는 개수로만 말한다(퍼센트·"정확도" 금지). 패널 설명은 모델 수·위치를 단정하지 않는다.
- 조건부 클래스는 템플릿 문자열 대신 `cn()`(`@/utils`).
- 체험 모드(`useDemoStore.active`)에서는 API 함수가 서버를 부르지 않는다(`store/useDemoStore.test.ts`가 지킨다). 이 계약을 깨지 않는다.
- 화면 확인: `.claude/launch.json`의 `frontend-mock`(포트 5175, `VITE_USE_MOCK=true`) 미리보기 + 체험 모드(`/?demo=1`). 라이트·다크·375px 너비를 모두 본다. 확인용으로 `mocks/`를 잠깐 바꾸면 커밋 전에 되돌린다.
- UI 문구·주석·문서는 한국어, 식별자는 영어. 문서에 날짜·PR 번호를 쓰지 않는다(위키 교훈 제목만 예외).
- **범위 밖:** 페이지 제목·`aria-live`·메뉴 Esc 같은 접근성 계획 항목, 중복 코드 정리, `AssessmentComparisonPanel.tsx:268`·`ConversationView.tsx:227`의 `outline-none`(앞은 접근성 계획, 뒤는 `tabIndex=-1`이라 제외), `ScrollStory` 말고 다른 파일의 템플릿 문자열 클래스. 이 PR이 먼저 머지되고 접근성 PR이 `MaterialListPage`·`ScrollStory`를 이어서 고친다.

## Review Focus

- 모델이 셋이 아니거나 일부가 연결 안 됨(`models[].status === 'not_configured'`): 칸 수는 `models` 길이를 따르고 그 칸은 "연결 안 됨" 글자, 패널 설명은 "세 칸"·"오른쪽"이라 말하지 않는다. → Task 1 테스트 `연결 안 된 모델은 '연결 안 됨'`, Task 2 화면 확인(목업 잠깐 수정).
- 연결은 됐지만 실패했거나(`failed`·`invalid_output`) 그 개념을 빠뜨린 모델: "연결 안 됨"이 아니라 "판단 없음". → Task 1 테스트.
- 다른 탭에서 로그인해 둔 상태로 이 탭에서 체험을 시작·새로고침: `msg-has-session`이 남아 다른 탭과 체험을 끝낸 뒤의 새 화면이 세션을 되살린다. → Task 4 테스트.
- 체험이 아닌데 Refresh Token이 만료돼 갱신이 실패: 지금처럼 표시까지 지운다(다음 새로고침에 헛요청을 보내지 않게). → Task 4 테스트.
- 375px에서 공백 없는 긴 자료 제목·파일명: 삭제 버튼이 줄 오른쪽에 남고 글자는 칸 안에서 줄바꿈·말줄임. → Task 6 화면 재현·확인.

---

### Task 1: 모델 표식 판단 글자 규칙

**문제:** `ModelVerdictMark`가 판단 글자와 "연결 안 됨"·"판단 없음"을 `title`·`sr-only`에만 둬서 화면(특히 호버가 없는 휴대폰)에는 아이콘만 보인다.

**Files:**
- Modify: `frontend/src/utils/analysis.ts` (`UNDERSTANDING_LABEL` 아래)
- Test: `frontend/src/utils/analysis.test.ts`

**Interfaces:**
- Produces: `verdictMarkLabel(status: UnderstandingStatus | null, modelStatus: ModelRunStatus): string` — 칸 아래 짧은 글자. `status`가 있으면 `understood` "이해", `partial` "부분 이해", `not_understood` "이해 부족", `insufficient_evidence` "근거 부족"(칸이 좁아 `UNDERSTANDING_LABEL`의 "판단 근거 부족"을 줄인다). `status`가 null이면 `modelStatus === 'not_configured'`일 때 "연결 안 됨", 그 밖에는 "판단 없음". `status`가 있으면 `modelStatus`는 보지 않는다. `@/utils` 배럴로 이미 내보내진다(`export * from './analysis'`). `ModelRunStatus`는 `@/types`에서 가져온다.

- [ ] **Step 1: 실패하는 테스트**

```ts
describe('verdictMarkLabel', () => {
  it('판단이 있으면 칸에 맞는 짧은 글자를 쓴다', () => {
    expect(verdictMarkLabel('understood', 'succeeded')).toBe('이해');
    expect(verdictMarkLabel('partial', 'succeeded')).toBe('부분 이해');
    expect(verdictMarkLabel('not_understood', 'succeeded')).toBe('이해 부족');
    expect(verdictMarkLabel('insufficient_evidence', 'succeeded')).toBe('근거 부족');
  });
  it("연결 안 된 모델은 '연결 안 됨'", () => {
    expect(verdictMarkLabel(null, 'not_configured')).toBe('연결 안 됨');
  });
  it("연결됐지만 실패했거나 그 개념을 빠뜨린 모델은 '판단 없음'", () => {
    expect(verdictMarkLabel(null, 'failed')).toBe('판단 없음');
    expect(verdictMarkLabel(null, 'invalid_output')).toBe('판단 없음');
    expect(verdictMarkLabel(null, 'succeeded')).toBe('판단 없음');
  });
});
```

- [ ] **Step 2: 실패 확인** — `cd frontend && npx vitest run src/utils/analysis.test.ts` → FAIL (`verdictMarkLabel` 없음).
- [ ] **Step 3: 구현** — 위 Interfaces대로. 짧은 글자 표는 `Record<UnderstandingStatus, string>` 상수(`VERDICT_MARK_LABEL`, 내보내지 않아도 됨)로 둔다.
- [ ] **Step 4: 통과 확인** — 같은 명령 → PASS.

### Task 2: 모델 표식 칸에 글자 보이기

**문제:** 위와 같음. 또 `title`과 `sr-only`가 같은 글을 두 번 읽히고, `<button>` 안에 `role="list"`/`listitem`을 둬 버튼 안 목록 의미가 깨진다. 패널 설명 "오른쪽 세 칸"은 lg 미만(칸이 이름 아래 줄)과 모델 수가 다를 때 틀린다.

**Files:**
- Modify: `frontend/src/pages/analysis/components/UnderstandingBadge.tsx:55-105` (`ModelVerdictMark`)
- Modify: `frontend/src/pages/analysis/components/ConceptList.tsx:104-124` (표식 묶음), `:194` (`Panel` `description`)
- Modify: `docs/frontend-guide.md:77` (`ModelVerdictMark` 설명)

**Interfaces:**
- Consumes: `verdictMarkLabel` (Task 1), `PROVIDER_LABEL` (기존).
- Produces: `ModelVerdictMark({ provider, status, label }: { provider: string; status: UnderstandingStatus | null; label: string })` — `missingLabel` prop은 없앤다(유일한 사용처가 `ConceptList`).

- [ ] **Step 1: `ModelVerdictMark` 칸 구조** — 한 칸을 위에서 아래로 `모델 이름`(지금 `text-[11px] text-ink-muted` 그대로) → 아이콘 칸(지금 `MARK_STYLE`·점선 그대로, 아이콘 `aria-hidden`) → 판단 글자 `label`(`text-caption text-ink-soft whitespace-nowrap`)로 쌓는다. DOM 순서가 곧 읽는 순서라 화면 읽기가 "GPT 부분 이해"로 읽는다. 바깥 `span`의 `role="listitem"`과 `title`, 모델 이름의 `aria-hidden`, `sr-only` 줄을 지운다. 칸 너비는 가장 긴 글자("부분 이해"·"연결 안 됨" 다섯 자)가 한 줄에 들어가는 고정 폭 하나(`w-16`부터, Task 7 화면에서 375px 세 칸이 한 줄에 들어가는지 보고 조정)로 모든 칸을 같게 둔다(같음·갈림이 모양으로 보이는 의도 유지). 위 주석 3줄도 새 구조에 맞게 고친다.
- [ ] **Step 2: `ConceptList` 표식 묶음** — 바깥 `span`의 `role="list"`·`aria-label`을 지우고, 첫 자식으로 `<span className="sr-only">모델별 판단 </span>`을 둔다(버튼 이름 안에서 숙련도와 모델 판단이 구분되게). 각 칸에는 `label={verdictMarkLabel(judgment?.status ?? null, model.status)}`를 넘긴다.
- [ ] **Step 3: 패널 설명** — `description`의 "오른쪽 세 칸은 모델마다의 판단이고,"를 "모델 이름이 붙은 칸은 모델마다의 판단이고,"로 바꾼다(나머지 문장 그대로).
- [ ] **Step 4: 문서** — `docs/frontend-guide.md:77`의 괄호 설명을 "같은 크기 칸에 위에서부터 모델 이름·판단 아이콘·짧은 판단 글자(`verdictMarkLabel`, 연결 안 됨·판단 없음 포함). 알약 대신 칸을 나란히 두어 같음·갈림이 모양으로 보인다"로 고친다.
- [ ] **Step 5: 확인** — `cd frontend && npm run lint && npx tsc -b` 통과. 화면은 Task 7.

### Task 3: 확인 창 제목·설명 id

**문제:** `ConfirmDialog`의 `aria-labelledby`·`aria-describedby`가 `confirm-dialog-title`·`confirm-dialog-description`으로 고정이다. 마이페이지(`pages/mypage/MyPage.tsx:114`·`:278`·`:413`)에 닫힌 `<dialog>` 3개가 함께 DOM에 있어 탈퇴 창이 첫 창(AI 퀴즈 동의 철회) 제목으로 읽힌다.

**Files:**
- Modify: `frontend/src/components/common/ConfirmDialog.tsx:1,23,35-36,50,57`

**Interfaces:** 없음(props 그대로).

- [ ] **Step 1: 구현** — `useId()`로 `titleId`·`descriptionId`를 만들어 `aria-labelledby`·`aria-describedby`(설명이 있을 때만)·`h2 id`·`p id`에 쓴다(`components/common/Tabs.tsx:27`의 `useId` 쓰는 방식과 같게).
- [ ] **Step 2: 확인** — `npm run lint && npx tsc -b` 통과. DOM 확인은 Task 7(DOM 테스트 환경이 없어 단위 테스트는 쓰지 않는다).

### Task 4: 체험 중 세션 복원이 공용 표시를 지우지 않게

**문제:** `restoreSession()`(`api/auth.api.ts:134-148`)이 체험 중(`useDemoStore.getState().active`)·`NO_BACKEND`일 때 `clearSession()`(`store/useAuthStore.ts:43-46`)을 불러 localStorage 표시 `msg-has-session`까지 지운다. 같은 브라우저에서 로그인해 둔 다른 탭이 새로고침하면 세션을 못 되살리고, 체험을 끝낸 뒤 새로 불러온 메인도 못 되살린다(`useDemoStore.test.ts:70-91` 기존 테스트는 `removeItem`을 빈 함수로 막아 이 문제를 못 잡는다).

**Files:**
- Modify: `frontend/src/store/useAuthStore.ts:6-12,35-47`
- Modify: `frontend/src/api/auth.api.ts:134-148`
- Test: `frontend/src/store/useDemoStore.test.ts` (기존 `restoreSession` 테스트 옆)

**Interfaces:**
- Produces: `AuthState.markAnonymous: () => void` — `set({ accessToken: null, status: 'anonymous' })`만 한다(표시는 건드리지 않음). 주석: "이 탭만 로그아웃 상태로 둔다. 다른 탭·다음 화면의 세션 복원 표시는 남긴다". `clearSession`은 그대로(로그아웃·탈퇴·갱신 실패용).
- `restoreSession()`: `NO_BACKEND || active || !hasSessionHint()` 분기에서 `clearSession()` 대신 `markAnonymous()`. `refreshAccessToken()` 실패 분기는 `clearSession()` 그대로.

- [ ] **Step 1: 실패하는 테스트** (`describe('실제 서버 빌드의 체험 모드')` 안)

```ts
it('체험 중 새로고침은 이 탭만 익명으로 두고, 다른 탭이 쓰는 로그인 표시는 지우지 않는다', async () => {
  const removeItem = vi.fn();
  vi.stubGlobal('localStorage', {
    getItem: (key: string) => (key === 'msg-has-session' ? '1' : null),
    setItem: () => undefined,
    removeItem,
    key: () => null,
    length: 0,
  });
  useDemoStore.getState().start();
  await restoreSession();
  expect(useAuthStore.getState().status).toBe('anonymous');
  expect(removeItem).not.toHaveBeenCalledWith('msg-has-session');
  vi.unstubAllGlobals();
});

it('체험이 아닌데 세션 갱신이 실패하면 로그인 표시를 지운다', async () => {
  const removeItem = vi.fn();
  vi.stubGlobal('localStorage', { /* 위와 같은 모양, removeItem */ });
  vi.spyOn(api, 'post').mockRejectedValue(new Error('쿠키 만료'));
  await restoreSession();
  expect(useAuthStore.getState().status).toBe('anonymous');
  expect(removeItem).toHaveBeenCalledWith('msg-has-session');
  vi.unstubAllGlobals();
});
```

(`useAuthStore`는 `@/store/useAuthStore`에서 가져온다. 두 번째 테스트는 이미 통과하는 대조 테스트다. 갱신 요청은 기존 테스트(`useDemoStore.test.ts:79-81`)처럼 `api.post`를 잡는다.)

- [ ] **Step 2: 실패 확인** — `cd frontend && npx vitest run src/store/useDemoStore.test.ts` → 첫 테스트 FAIL(`removeItem('msg-has-session')` 호출됨), 두 번째 PASS.
- [ ] **Step 3: 구현** — 위 Interfaces대로. `restoreSession` 위 주석에 "표시는 지우지 않는다(같은 브라우저의 로그인한 다른 탭·체험을 끝낸 뒤 새 화면이 되살릴 수 있게)" 한 줄을 더한다.
- [ ] **Step 4: 통과 확인** — 같은 명령 → 전부 PASS. 이어서 `npx vitest run src/api` PASS(axios 401 갱신 실패는 `clearSession` 그대로).

### Task 5: `outline-none` → `outline-hidden`

**문제:** Tailwind 4에서 `outline-none`은 `outline-style: none`이라 고대비(강제 색) 모드에서 포커스 표시가 사라진다. 포커스 표시를 테두리로 대신하는 입력칸은 `outline-hidden`(투명 outline, 강제 색 모드에서는 보임)을 써야 한다.

**Files:**
- Modify: `frontend/src/components/common/fieldClass.ts:3`
- Modify: `frontend/src/components/layout/Header.tsx:264`
- Modify: `frontend/src/pages/courses/components/KeywordInput.tsx:62`
- Modify: `frontend/CLAUDE.md:127` (접근성 줄)

- [ ] **Step 1: 바꾸기** — 세 곳의 `outline-none`을 `outline-hidden`으로. 그 뒤 `grep -rn "outline-none" frontend/src`의 결과가 `AssessmentComparisonPanel.tsx:268`·`ConversationView.tsx:227` 두 줄만이어야 한다(범위 밖). 새로 나온 곳이 있으면 같이 바꾼다.
- [ ] **Step 2: 규칙 한 줄** — `frontend/CLAUDE.md`의 "접근성:" 줄 끝에 "포커스 테두리를 숨길 때는 `outline-hidden`(Tailwind 4의 `outline-none`은 고대비 모드에서 포커스까지 지운다)."를 덧붙인다(새 줄을 만들지 않는다).
- [ ] **Step 3: 확인** — `npm run build` 뒤 `grep -o "\.outline-hidden{[^}]*}" frontend/dist/assets/*.css`에 `outline:2px solid transparent`·`outline-offset:2px`가 있다(규칙 모양이 다르면 그대로 보고). 화면 포커스는 Task 7.

### Task 6: 자료 목록 한 줄이 375px에서 무너지지 않게

**문제:** `LIST_ROW_CLASS`(`components/common/listPanelClass.ts:7`)가 `flex-wrap`인데 자료 목록의 정보 칸(`pages/materials/MaterialListPage.tsx:88`)에 `flex-1`이 없어, 긴 파일명이면 정보 칸이 내용 폭을 고집해 삭제 버튼이 다음 줄 왼쪽으로 떨어질 수 있다. 본문 `break-keep`이라 공백 없는 긴 제목은 줄바꿈도 안 된다. **화면으로 아직 확인하지 않았다.**

**Files:**
- Modify: `frontend/src/pages/materials/MaterialListPage.tsx:88-93`

- [ ] **Step 1: 재현** — `frontend-mock` 미리보기, 체험 모드, 375px(`resize_window` preset mobile), `/materials`. 체험에서 자료 올리기로 공백 없는 긴 이름 파일(예: `정보보호개론_3주차_공개키암호_정리노트_최종수정본_v2.pdf`)을 올리거나, 안 되면 `mocks/`의 자료 하나의 `title`·`fileName`을 잠깐 그렇게 바꾼다. 삭제 버튼 위치를 스크린샷으로 남긴다. 재현되지 않아도 Step 2는 한다(가로 넘침·버튼 밀림 둘 다 막는 수정이라).
- [ ] **Step 2: 고치기** — 정보 칸을 `flex min-w-0 flex-1 flex-col gap-1.5`로, 제목 `Link`에 `break-words`(프로젝트에서 쓰는 방식, `SourceQuote.tsx:21`)를 더한다. `LIST_ROW_CLASS` 자체와 `QuizCard`는 바꾸지 않는다(퀴즈 줄은 글자 버튼이 다음 줄로 내려가는 게 의도).
- [ ] **Step 3: 확인** — 같은 화면 375px·데스크탑에서 삭제 버튼이 줄 오른쪽 위에 남고, 제목은 줄바꿈·파일명은 말줄임. 바꾼 `mocks/`는 되돌린다.

### Task 7: `ScrollStory` 클래스 정리, 전체 확인, 마무리

**Files:**
- Modify: `frontend/src/pages/main/components/story/ScrollStory.tsx:1-13` (import), `:125-129`
- Create: `docs/wiki/frontend.md`에 교훈 한 건(파일은 있음, 항목 추가)

- [ ] **Step 1: `cn()`** — `ScrollStory.tsx:125`의 템플릿 문자열을 `cn('flex min-h-10 w-full items-center border-l-2 pl-4 text-left transition-colors', active ? 'border-primary font-semibold text-heading' : 'border-line text-ink-muted hover:text-heading')`로 바꾸고 `import { cn } from '@/utils';`를 더한다. 화면은 그대로여야 한다.
- [ ] **Step 2: 검사** — `cd frontend && npm run lint && npm run format && npm run format:check && npm test && npm run build` 모두 통과.
- [ ] **Step 3: 화면 확인** (`frontend-mock` 미리보기, `/?demo=1`로 체험 시작, 라이트·다크·375px 각각 스크린샷)
  - 학습 대시보드 개념 목록: 칸마다 모델 이름·아이콘·판단 글자가 보이고, 375px에서 세 칸이 한 줄에 들어가며 숙련도 줄과 겹치지 않는다. 다크에서 글자 대비가 충분하다. 패널 설명이 새 문구다.
  - 연결 안 됨: `mocks/analysis.ts:124-130`의 `gemini`를 잠깐 `status: 'not_configured'`로, 그 모델의 `judgments`가 없게 바꿔 칸에 "연결 안 됨"이 보이는지 본 뒤 되돌린다.
  - 버튼 이름: `read_page`로 개념 줄 버튼이 "… 모델별 판단 GPT 이해 부족 Claude …"처럼 한 번씩만 읽히는지(`title`·중복 없음).
  - 확인 창: `/mypage`에서 `javascript_tool`로 `[...document.querySelectorAll('dialog')].map(d => document.getElementById(d.getAttribute('aria-labelledby'))?.textContent)`가 창마다 자기 제목 3개를 돌려준다. 탈퇴 버튼으로 창을 열어 제목·버튼이 맞는지 본다(누르지 않고 취소).
  - 포커스: 키보드 Tab으로 상단바 검색칸·과목 키워드 입력칸·일반 입력칸에 들어가면 테두리 색으로 포커스가 보인다(바깥 outline은 안 보이는 게 정상).
  - 메인 스크롤 소개 단계 목록: 지금 단계 표시가 그대로.
  - 자료 목록: Task 6 결과.
- [ ] **Step 4: 위키** — `wiki-lesson` 스킬로 `docs/wiki/frontend.md`에 "Tailwind 4의 `outline-none`은 고대비 모드에서 포커스를 지운다: `outline-hidden`을 쓴다" 한 건(상황·원인·해결·다음에는). 작업 중 다른 시행착오가 있었으면 그것도.
- [ ] **Step 5: 사용자 확인 뒤 한 번에 커밋** — 계획 문서 포함. `git diff --stat`에 `mocks/` 변경이 없는지 먼저 본다. PR 본문에 고친 6건, 화면 변경(대시보드 개념 줄의 모델 칸 모양이 바뀌어 README `docs/images/screen-concept-detail.png`·노션·시연 영상 캡처를 다시 찍어야 하는지), 뒤따를 접근성 PR이 `MaterialListPage`·`ScrollStory`를 건드린다는 점을 적는다. 푸시·PR은 사용자가 요청할 때만.
