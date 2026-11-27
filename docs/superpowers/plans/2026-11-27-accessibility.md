# 접근성 (WCAG 2.2 AA) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 화면 제목·포커스 이동·알림·포커스 표시·대비에서 WCAG 2.2 AA를 어기는 11곳을 고쳐, 키보드와 화면 낭독기만으로 체험 모드 흐름을 끝까지 쓸 수 있게 한다.

**Architecture:** 세 PR로 나눈다. PR 1은 공통 틀(라우트 제목·이동 알림·본문 건너뛰기·`scroll-padding-top`·주 메뉴 Esc), PR 2는 늘 있는 live 영역(AI 대기·삭제 알림·메인 스크롤 소개), PR 3은 작은 포커스·대비·라벨 수정이다. 판단이 들어가는 로직(제목 고르기, 완료 문구 결정, 단계 알림 문장)만 순수 함수로 빼서 Vitest로 지키고, 나머지는 화면·키보드·VoiceOver 확인 단계로 확인한다. API·DTO·색 토큰은 바꾸지 않는다.

**Tech Stack:** React 19, TypeScript 6, React Router 7(데이터 라우터 `handle`·`useMatches`), Tailwind CSS 4, Motion, Vitest(node 환경, `src/**/*.test.ts`만).

**Spec:** 별도 spec 문서 없음. 이 계획을 요청한 접근성 검토 결과 11개 항목(아래 "검토 항목과 Task" 표)이 spec이다.

## Global Constraints

- **선행:** 프론트 회귀 계획(`docs/superpowers/plans/2026-11-27-frontend-regressions.md`: 3칸 표식 글자, `ConfirmDialog` useId, 체험 세션 표시, `outline-none`→`outline-hidden` 3곳, `MaterialListPage` 줄 배치, `ScrollStory` cn())이 먼저 main에 머지된다. 그 항목은 여기서 하지 않는다. 세 PR 모두 **그 PR 머지 뒤 main에서** 브랜치를 딴다(`MaterialListPage`·`ScrollStory`·`fieldClass.ts`·`Header.tsx`가 겹친다). 아래 줄 번호는 756f6f7 기준이라 회귀 PR 뒤 다시 찾는다.
- 브랜치: PR 1 `fix/a11y-navigation`, PR 2 `fix/a11y-live-status`, PR 3 `fix/a11y-focus-contrast`. 한 PR에는 한 가지 묶음만.
- 커밋: `fix: 화면마다 페이지 제목을 정하고 이동을 알리기`처럼 타입 + 한국어 설명. 끝에 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- **화면을 확인받으며 고치는 작업이라 Task마다 커밋하지 않는다.** 작업 트리에만 반영하고, PR마다 마지막 Task에서 화면 확인을 받은 뒤 한 번에 커밋한다.
- 검증: `cd frontend && npm run lint && npm run format:check && npm test && npm run build` 모두 통과(형식이 틀리면 `npm run format`).
- 테스트는 `src/**/*.test.ts`(node 환경, DOM·testing-library 없음)라 순수 함수만 테스트한다. 테스트에서 `node:` 모듈을 가져오지 않는다(`tsc -b`가 깨진다, `docs/wiki/frontend.md`).
- 컴포넌트 파일은 컴포넌트만 내보낸다. 상수·도우미 함수는 같은 폴더 소문자 파일로(ESLint `react-refresh/only-export-components`).
- 조건부 클래스는 `cn()`(`@/utils`). 색 코드(`text-[#…]`, `bg-white`)와 `dark:` 클래스 금지. 다크 값은 `src/index.css`의 `:root.dark` 토큰으로만.
- 이 계획은 색 토큰을 추가·변경하지 않는다(9번은 조합만 바꾼다). 바꾸게 되면 `styles/theme-transition.css`에 `@property` 등록 + `transition` 목록 추가 + 다크 값 + 글자·바탕 대비 4.5:1 확인.
- 움직임: `motion.*` 대신 `m.*`. "동작 줄이기"에서도 정보를 잃지 않는다(움직임으로만 알리지 않는다). 새 CSS 전환에는 `motion-reduce:transition-none`.
- UI 문구·주석은 한국어, 식별자는 영어. 버튼은 실제 동작을 쓴다. "정확도" 표현 금지. AI 대기 상자는 AI 보라가 아니라 지금처럼 `primary-soft` 바탕.
- 화면 확인: `.claude/launch.json`의 `frontend-mock`(포트 5175) 미리보기에서 `?demo=1`로 체험 모드. 확인은 **마우스 없이 키보드(Tab·Shift+Tab·Enter·Space·Esc)만**으로 한다. 라이트·다크 둘 다.
- **[사람]** 표시 단계는 macOS VoiceOver(⌘F5) 확인이라 에이전트가 하지 않고 사용자에게 부탁한다.
- 시행착오가 있으면 `docs/wiki/frontend.md`에 교훈 한 건(작성 규칙은 `docs/wiki/README.md`). 문서에는 경과(날짜·PR 번호)를 쓰지 않는다(위키 제외).
- 화면이 바뀌면 PR 본문에 제출 자료(노션·영상) 갱신 필요 여부를 적는다. 머지 전에 PR 미리보기의 체험 모드로 확인한다.

## 검토 항목과 Task

| # | 항목 (기준) | 확인한 위치 (756f6f7) | Task |
| --- | --- | --- | --- |
| 1 | 화면마다 같은 제목, 이동 알림 없음 (2.4.2, 4.1.3) | `index.html:7`, `router/index.tsx` | 1, 2 |
| 3 | 하위 메뉴 Esc로 못 닫음 (1.4.13), 본문 건너뛰기 없음 (2.4.1) | `layout/Gnb.tsx:177`, `layout/Layout.tsx` | 2, 4 |
| 4 | sticky 띠·상단바가 포커스를 가림 (2.4.11) | `layout/Layout.tsx:15` | 3 |
| 6 | AI 대기 live 상자가 대기 중에만 붙음 (4.1.3) | `common/AiWaiting.tsx:25` + 사용처 9곳 | 6, 7 |
| 7 | 삭제 뒤 포커스가 body로 빠지고 알림 없음 (2.4.3, 4.1.3) | `pages/materials/MaterialListPage.tsx:102-110,187-190` | 8 |
| 8 | 넓은 화면 스크롤 소개는 현재 단계만 DOM에 있음 (1.3.1, 4.1.3) | `pages/main/components/story/ScrollStory.tsx:96-163` | 9 |
| 2 | 막대 줄 포커스가 안 보임 (2.4.7) | `pages/analysis/components/AssessmentComparisonPanel.tsx:261-268` | 11 |
| 11 | 입력칸 포커스가 1px 테두리 색 변화뿐 (1.4.11) | `common/fieldClass.ts:3` | 11 |
| 5 | 보이는 글이 접근 이름에 없음 (2.5.3) | `pages/analysis/components/LayoutReasonPopover.tsx:52-68` | 12 |
| 9 | `ink-muted` on `primary-soft` 다크 4.41:1 (1.4.3) | `pages/quizzes/QuizCreatePage.tsx:500`, `common/FileDropZone.tsx:60` | 12 |
| 10 | 세션 되살리는 중에도 체험 버튼이 보임 | `common/DemoStartButton.tsx:28` | 12 |

## 1번 방식 결정: 라우트 `handle.title`

`SubPage`가 아니라 라우트 메타(`handle: { title }`)에서 정하고 `Layout` 안의 `RouteAnnouncer` 하나가 `useMatches()`로 읽는다.

- `SubPage`를 쓰지 않는 화면이 있다: 메인, 로그인·회원가입·카카오 3화면, 404(`NotFoundPage`). `SubPage`에서 정하면 이 화면들에 따로 넣어야 하고 빠뜨리기 쉽다.
- 라우트 메타는 페이지 코드·데이터를 불러오기 전에 이미 있어서, `lazy`·`useSuspenseQuery` 로딩 중에도 제목과 이동 알림이 바로 맞다. `SubPage`는 그려진 뒤에야 정해진다.
- 주소와 제목이 `router/index.tsx` 한 파일에 나란히 있어, 새 화면을 추가할 때 같이 쓰게 된다(라우트 추가 규칙에 한 줄 더함).
- 상세 화면(`materials/:id`, `quizzes/:quizId`)은 "학습자료", "퀴즈 풀기"처럼 고정 제목으로 둔다. 자료 이름까지 넣는 것은 AA 요건이 아니고, 넣으려면 데이터를 읽는 화면마다 덮어써야 한다(열린 질문).
- 라우터 루트의 `errorElement`(`RouteErrorPage`)는 `Layout` 밖이라 자기 제목을 직접 정한다.

## Review Focus

- 체험 띠가 있을 때·없을 때, 휴대폰 폭(상단바 57px)·넓은 화면(80px)에서 Shift+Tab으로 위로 올라갈 때 포커스된 요소가 띠·상단바 밑에 숨지 않아야 한다. → Task 3 Step 3 확인 항목(높이를 재서 `--sticky-offset`에 넣으므로 띠 유무를 따라간다).
- 주소의 검색어만 바뀌는 이동(`/search?q=a` → `?q=b`, `/quizzes/new?mode=…`)과 본문 건너뛰기 링크는 이동 알림·포커스 이동을 하지 않아야 한다. → Task 2 Step 4 확인 항목(`pathname`이 바뀔 때만).
- 처음 그릴 때 이미 끝난 상태(요약 `ready`, 채점 끝난 결과)에서 "다 만들었어요"를 읽지 않고, 실패로 끝나면 완료 문구 대신 `ErrorText`의 `role="alert"`만 읽혀야 한다. → Task 6 테스트 `처음부터 대기 중이 아니면 비운다`, `실패로 끝나면 비운다`.
- 요약 만드는 중에 목적(시험 대비 등)을 바꾸면 다른 목적의 `ready` 상태를 "다 만들었어요"로 읽으면 안 된다. → Task 7 Step 2(`key={purpose}`로 새로 그린다).
- 마지막 자료·과목을 지워 빈 화면(`EmptyState`)이 될 때도 포커스가 body로 빠지지 않고 삭제 알림을 읽어야 한다. → Task 8 Step 1(빈 화면 분기에도 알림 문단을 둔다), Step 4 확인 항목.

---

## PR 1 — `fix/a11y-navigation`

### Task 1: 화면 제목 고르기 함수

**Files:**
- Create: `frontend/src/router/routeTitle.ts`
- Test: `frontend/src/router/routeTitle.test.ts`

**Interfaces:**
- Produces:
  - `interface RouteHandle { title: string }`
  - `const SITE_TITLE = 'MSG · My Study Guide'` (`index.html:7`과 같은 값)
  - `pageTitle(matches: readonly { handle?: unknown }[]): string | null` — 뒤에서부터 `handle.title`이 문자열인 첫 항목의 값, 없으면 `null`.
  - `documentTitle(page: string | null): string` — `page`가 있으면 `` `${page} · MSG` ``, 없으면 `SITE_TITLE`.

- [ ] **Step 1: 실패하는 테스트**

```ts
describe('pageTitle', () => {
  it('가장 안쪽 라우트의 제목을 고른다', () => {
    expect(pageTitle([{ handle: undefined }, { handle: { title: '퀴즈' } }, { handle: { title: '퀴즈 결과' } }])).toBe('퀴즈 결과');
  });
  it('안쪽에 제목이 없으면 바깥 제목을 쓴다', () => {
    expect(pageTitle([{ handle: { title: '공지 관리' } }, { handle: undefined }])).toBe('공지 관리');
  });
  it('제목이 없거나 문자열이 아니면 null', () => {
    expect(pageTitle([{ handle: undefined }, { handle: { title: 3 } }, { handle: null }])).toBeNull();
  });
});
describe('documentTitle', () => {
  it('페이지 제목 뒤에 서비스 이름을 붙인다', () => {
    expect(documentTitle('내 학습자료')).toBe('내 학습자료 · MSG');
  });
  it('제목이 없으면(메인) 사이트 제목', () => {
    expect(documentTitle(null)).toBe('MSG · My Study Guide');
  });
});
```

- [ ] **Step 2: 실패 확인** — `cd frontend && npx vitest run src/router/routeTitle.test.ts` → FAIL(모듈 없음).
- [ ] **Step 3: 구현** — 위 Interfaces대로 `routeTitle.ts`에 쓴다(React·라우터 import 없이 순수 함수만).
- [ ] **Step 4: 통과 확인** — 같은 명령 → PASS.

### Task 2: 라우트 제목·이동 알림·본문 건너뛰기

**Files:**
- Modify: `frontend/src/router/index.tsx` (각 라우트 객체에 `handle`, 맨 위 "페이지 추가 시" 주석에 한 줄)
- Create: `frontend/src/components/layout/RouteAnnouncer.tsx`
- Create: `frontend/src/components/layout/mainContent.ts`
- Modify: `frontend/src/components/layout/Layout.tsx` (건너뛰기 링크, `main`에 id·`tabIndex`, `RouteAnnouncer`)
- Modify: `frontend/src/pages/RouteErrorPage.tsx` (자기 제목)
- Modify: `frontend/CLAUDE.md` 코드 규칙의 "페이지는 `router/index.tsx`에…" 줄

**Interfaces:**
- Consumes: `pageTitle`, `documentTitle`, `RouteHandle` (Task 1).
- Produces: `MAIN_CONTENT_ID = 'main-content'` (`mainContent.ts`), `focusMainContent(): void` (같은 파일, `document.getElementById(MAIN_CONTENT_ID)?.focus({ preventScroll: true })`). `RouteAnnouncer`와 건너뛰기 링크가 쓴다.

- [ ] **Step 1: 라우트 제목** — `router/index.tsx`의 페이지 라우트마다 `handle: { title } satisfies RouteHandle`를 단다. 메인(index)·`RequireAuth`·`RequireAdmin`·`admin` 리다이렉트에는 달지 않는다.

| 경로 | 제목 | 경로 | 제목 |
| --- | --- | --- | --- |
| `courses` | 내 과목 | `mypage` | 마이페이지 |
| `materials` | 내 학습자료 | `admin/notices` | 공지 관리 |
| `materials/upload` | 자료 올리기 | `admin/notices/new` | 새 공지 쓰기 |
| `materials/:materialId` | 학습자료 | `admin/notices/:noticeId/edit` | 공지 고치기 |
| `curriculum` | 맞춤 커리큘럼 | `notices`, `notices/:noticeId` | 공지사항 |
| `quizzes` | 퀴즈 | `about` | 서비스 소개 |
| `assessments` | 사전·사후평가 | `terms` | 이용약관 |
| `quizzes/new` | 퀴즈 만들기 | `privacy` | 개인정보 처리방침 |
| `quizzes/:quizId` | 퀴즈 풀기 | `analysis/import/help/:service` | 대화 내보내기 도움말 |
| `quizzes/:quizId/result` | 퀴즈 결과 | `login` / `signup` | 로그인 / 회원가입 |
| `analysis` | 학습 대시보드 | `signup/social` | 카카오로 가입하기 |
| `analysis/import` | AI 대화 가져오기 | `auth/social/done` | 카카오 로그인 |
| `search` | 검색 결과 | `auth/social/link` | 카카오 계정 연결 |
| `*` | 페이지를 찾을 수 없어요 | | |

  "페이지 추가 시" 주석 2번에 "`handle: { title }`(브라우저 탭 제목·이동 알림)도 함께 단다"를 넣는다.

- [ ] **Step 2: `RouteAnnouncer()` 컴포넌트** — `useMatches()`·`useLocation()`을 읽어:
  - `documentTitle(pageTitle(matches))`가 바뀔 때마다 `document.title`에 넣는다(첫 화면 포함).
  - `pathname`이 바뀔 때만(첫 렌더·검색어·해시 변경 제외, 이전 값은 `useRef`) 시각적으로 숨긴 `<p aria-live="polite" className="sr-only">`에 페이지 제목(없으면 "메인")을 넣는다.
  - 같은 효과 안에서 `document.activeElement`가 `body`이거나 없으면(눌렀던 링크가 이전 화면과 함께 사라짐) `focusMainContent()`. 상단바 메뉴에서 이동해 포커스가 상단바에 남아 있으면 옮기지 않는다(메뉴를 이어서 쓸 수 있게).
- [ ] **Step 3: `Layout`** — 맨 앞에 건너뛰기 링크 `<a href={`#${MAIN_CONTENT_ID}`}>본문으로 건너뛰기</a>`: 평소 `sr-only`, 포커스되면 `focus:not-sr-only focus:fixed focus:top-3 focus:left-3 focus:z-50 focus:rounded-lg focus:bg-surface-raised focus:px-4 focus:py-2 focus:font-semibold focus:text-primary focus:shadow-md focus:shadow-shade/10`. `onClick`에서 `preventDefault()` 뒤 `main`에 `focus()` + `scrollIntoView({ block: 'start' })`(주소에 `#main-content`가 붙어 기록이 하나 더 쌓이지 않게). 건너뛰기 링크는 `pointer-events-none` 묶음 밖에 둔다. `<main id={MAIN_CONTENT_ID} tabIndex={-1} className="flex-1 outline-hidden">`(코드로만 포커스하는 상자라 테두리를 그리지 않는다). `<RouteAnnouncer />`는 `Outlet` 바깥 `Layout` 안 아무 곳.
- [ ] **Step 4: `RouteErrorPage`** — `useEffect`로 `document.title = documentTitle(chunkError ? '새 버전이 나왔어요' : '화면을 보여 드리지 못했어요')`.
- [ ] **Step 5: `frontend/CLAUDE.md`** — 코드 규칙 "페이지는 `router/index.tsx`에 `lazyPage(...)`로 추가한다" 줄 끝에 "`handle: { title }`도 단다(탭 제목·이동 알림, `RouteAnnouncer`)."를 붙인다(새 줄을 만들지 않는다).
- [ ] **Step 6: 확인** — `npm run lint && npx tsc -b` 통과. 미리보기(`frontend-mock`, `?demo=1`)에서 키보드만으로:
  - 주 메뉴로 "내 학습자료" → 탭 제목 "내 학습자료 · MSG", 포커스는 상단바에 남음.
  - 자료 목록의 자료 링크(Enter) → 탭 제목 "학습자료 · MSG", 다음 Tab이 본문 첫 링크로 감(상단바 처음으로 돌아가지 않음).
  - 새로고침 직후 첫 Tab에 "본문으로 건너뛰기"가 왼쪽 위에 보이고, Enter 뒤 Tab이 본문으로 감. 주소에 `#`이 붙지 않음.
  - 검색칸에서 검색어만 바꿔 다시 검색해도 포커스가 검색칸에 남음.
  - 없는 주소(`/nope`) → "페이지를 찾을 수 없어요 · MSG".
- [ ] **Step 7 [사람]:** VoiceOver로 메뉴 이동 시 새 페이지 제목을 한 번 읽는지, 본문 링크로 이동 뒤 VO 커서가 본문에 있는지.

### Task 3: 위에 붙은 띠·상단바가 포커스를 가리지 않게

**Files:**
- Create: `frontend/src/components/layout/useStickyOffset.ts`
- Modify: `frontend/src/components/layout/Layout.tsx:15` (묶음 `div`에 ref)
- Modify: `frontend/src/index.css` (`@layer base`)
- Modify (`scroll-mt-*` 빼기): `components/common/LegalDocument.tsx:127`, `pages/materials/components/MaterialConceptPanel.tsx:153`, `pages/analysis/DashboardPage.tsx:392,397`, `pages/analysis/components/ConceptList.tsx:57`, `pages/curriculum/CurriculumPage.tsx:156`

**Interfaces:**
- Produces: `useStickyOffset(ref: RefObject<HTMLElement | null>): void` — `ResizeObserver`로 요소의 `offsetHeight`(transform으로 숨은 상단바도 자리 높이는 그대로)를 재서 `document.documentElement.style.setProperty('--sticky-offset', `${h}px`)`, 정리할 때 `removeProperty`.

- [ ] **Step 1:** `useStickyOffset` 구현, `Layout`의 sticky 묶음에 ref를 달아 부른다.
- [ ] **Step 2:** `index.css` `@layer base`에 `html { scroll-padding-top: var(--sticky-offset, 0px); }`와 이유 주석(포커스·앵커 이동 때 띠·상단바 밑으로 숨지 않게). 위 6곳의 `scroll-mt-24`·`scroll-mt-28`은 `scroll-padding-top`과 더해져 간격이 두 배가 되므로 지운다.
- [ ] **Step 3: 확인** — 체험 모드 켠/끈 상태, 창 폭 375px·1280px에서: 긴 화면(학습 대시보드)을 내려 상단바를 숨긴 뒤 Shift+Tab으로 위쪽 요소로 올라갈 때 포커스 요소가 띠·상단바 밑에 가리지 않음. 대시보드 요약 칸 "자세히"(앵커)·커리큘럼에서 개념으로 가기(`MaterialConceptPanel` 스크롤)·약관 목차 링크가 제목이 상단바 바로 아래에 오게 멈춤. 개발자 도구에서 `getComputedStyle(document.documentElement).scrollPaddingTop`이 띠+상단바 높이.

### Task 4: 주 메뉴 하위 메뉴 Esc로 닫기

**Files:**
- Modify: `frontend/src/components/layout/Gnb.tsx:147-189`

**Interfaces:** 없음 (컴포넌트 안 상태만).

- [ ] **Step 1:** 상태 `closedIndex: number | null`과 대메뉴 링크 ref 배열을 둔다. 각 `li`에:
  - `onKeyDown`: `Escape`면 `setClosedIndex(index)`, 포커스가 하위 메뉴 안에 있으면 그 묶음의 대메뉴 링크로 옮긴다.
  - `onMouseEnter`: 지금 동작 + `setClosedIndex(null)`(다시 들어오면 열림).
  - `onFocus`: 지금 동작 + 포커스가 `li` 밖에서 들어왔을 때만(`!e.currentTarget.contains(e.relatedTarget)`) `setClosedIndex(null)`(Esc 뒤 대메뉴로 옮긴 포커스가 다시 열지 않게).
- [ ] **Step 2:** 하위 메뉴 `ul`의 className을 `cn()`으로 나눠, `closedIndex === index`면 `group-hover:*`·`group-has-[:focus-visible]:*` 묶음을 빼고 기본(`invisible opacity-0 translate-y-1`)만 남긴다.
- [ ] **Step 3: 확인** — 넓은 화면에서 Tab으로 "학습자료" → 하위 메뉴 열림 → Esc → 닫히고 포커스는 "학습자료"에 → Tab으로 "학습분석"에 가면 그 하위 메뉴는 열림. 하위 메뉴 항목에 포커스가 있을 때 Esc → 닫히고 포커스가 "학습자료". 마우스를 올린 채 Esc → 닫힘, 나갔다 다시 올리면 열림. 동작 줄이기 켬에서도 같은지.

### Task 5: PR 1 마무리

- [ ] **Step 1:** `cd frontend && npm run lint && npm run format:check && npm test && npm run build` 통과.
- [ ] **Step 2:** Task 2·3·4 확인 항목을 라이트·다크로 다시 훑고 스크린샷(건너뛰기 링크 포커스, Esc로 닫힌 메뉴)으로 사용자 확인을 받는다.
- [ ] **Step 3 [사람]:** VoiceOver로 Task 2 Step 7.
- [ ] **Step 4:** 확인 뒤 한 번에 커밋(이 계획 문서 포함) `fix: 화면마다 페이지 제목을 정하고 이동·본문 건너뛰기·메뉴 Esc를 키보드로 쓰게`. PR 본문에 화면 변화(건너뛰기 링크는 포커스할 때만 보임, 제출 자료 갱신 불필요)와 `scroll-mt-*` 제거로 앵커 위치가 바뀐 화면을 적는다.

---

## PR 2 — `fix/a11y-live-status`

### Task 6: AI 대기 완료 문구 결정 함수

**Files:**
- Create: `frontend/src/components/common/aiWaitingStatus.ts`
- Test: `frontend/src/components/common/aiWaitingStatus.test.ts`

**Interfaces:**
- Produces: `nextDoneMessage(prev: { waiting: boolean; message: string }, next: { waiting: boolean; failed: boolean }, doneText?: string): string`
  - `next.waiting` → `''` (대기 중에는 보이는 대기 상자 자체가 live 영역 안에서 읽힌다)
  - `prev.waiting && !next.waiting` → `next.failed || !doneText ? '' : doneText`
  - 그 밖 → `prev.message` (그대로 둔다)

- [ ] **Step 1: 실패하는 테스트**

```ts
describe('nextDoneMessage', () => {
  const idle = { waiting: false, message: '' };
  it('대기가 성공으로 끝나면 완료 문구', () => {
    expect(nextDoneMessage({ waiting: true, message: '' }, { waiting: false, failed: false }, '요약을 다 만들었어요.')).toBe('요약을 다 만들었어요.');
  });
  it('실패로 끝나면 비운다 (오류는 ErrorText가 읽는다)', () => {
    expect(nextDoneMessage({ waiting: true, message: '' }, { waiting: false, failed: true }, '요약을 다 만들었어요.')).toBe('');
  });
  it('완료 문구가 없으면(끝나면 다른 화면으로 감) 비운다', () => {
    expect(nextDoneMessage({ waiting: true, message: '' }, { waiting: false, failed: false })).toBe('');
  });
  it('처음부터 대기 중이 아니면 비운다', () => {
    expect(nextDoneMessage(idle, { waiting: false, failed: false }, '다 만들었어요.')).toBe('');
  });
  it('다시 기다리기 시작하면 이전 완료 문구를 지운다', () => {
    expect(nextDoneMessage({ waiting: false, message: '다 만들었어요.' }, { waiting: true, failed: false }, '다 만들었어요.')).toBe('');
  });
  it('대기 상태가 그대로면 문구를 유지한다', () => {
    expect(nextDoneMessage({ waiting: false, message: '다 만들었어요.' }, { waiting: false, failed: false }, '다 만들었어요.')).toBe('다 만들었어요.');
  });
});
```

- [ ] **Step 2: 실패 확인** — `npx vitest run src/components/common/aiWaitingStatus.test.ts` → FAIL.
- [ ] **Step 3: 구현** — 위 규칙대로.
- [ ] **Step 4: 통과 확인** — 같은 명령 → PASS.

### Task 7: 늘 있는 AI 대기 live 영역 `AiWaitingStatus`

**Files:**
- Create: `frontend/src/components/common/AiWaitingStatus.tsx`, `common/index.ts`에 배럴 추가
- Modify: `frontend/src/components/common/AiWaiting.tsx` (`live` prop·`aria-live` 제거, 주석 갱신)
- Modify (사용처): `pages/materials/components/MaterialConceptPanel.tsx:234-238, 395-399`, `pages/materials/components/StudySummaryPanel.tsx:271-280, 309-314`, `pages/quizzes/QuizSolvePage.tsx:281-286`, `pages/quizzes/QuizResultPage.tsx:385-390`, `pages/quizzes/QuizCreatePage.tsx:89-121(PendingGeneration), 697-705, 719-724`, `pages/assessments/AssessmentPage.tsx:321-326`, `pages/analysis/DashboardPage.tsx:298, 305`(`live={false}`만 지움)
- Modify: `docs/frontend-guide.md` 1절의 `AiWaiting` 설명

**Interfaces:**
- Consumes: `nextDoneMessage` (Task 6), `AiWaiting`.
- Produces: `AiWaitingStatus({ waiting, failed = false, doneText, state, className, children }: { waiting: boolean; failed?: boolean; doneText?: string; state?: OrbState; className?: string; children: ReactNode })` — 늘 그려지는 `<div aria-live="polite">`. 대기 중이면 그 안에 `<AiWaiting state className>{children}</AiWaiting>`, 아니면 완료 문구(없으면 빈 칸)만 담고 자신은 `sr-only`(빈 자리·flex 간격이 생기지 않게). 이전 `waiting`·문구는 "렌더 중 상태 조정"(`if (waiting !== prevWaiting) { setPrevWaiting(waiting); setMessage(nextDoneMessage(...)) }`)으로 고른다(효과로 하면 한 번 더 그린다).

- [ ] **Step 1:** `AiWaitingStatus` 구현, `AiWaiting`에서 `live`·`aria-live`를 지우고 "live 영역은 `AiWaitingStatus`나 바깥 상자가 맡는다" 주석. `DashboardPage`는 이미 늘 있는 live 상자라 `live={false}`만 지운다.
- [ ] **Step 2:** 사용처를 `{cond && <AiWaiting>…</AiWaiting>}`에서 `<AiWaitingStatus waiting={cond} failed={…} doneText="…">…</AiWaitingStatus>`로 바꾼다. 안내 글(children)은 그대로.

| 위치 | `waiting` | `failed` | `doneText` |
| --- | --- | --- | --- |
| MaterialConceptPanel 설명 쓰기 | `mutation.isPending` | `mutation.isError` | AI 설명을 다 썼어요. |
| MaterialConceptPanel 개념 찾기 | `mutation.isPending` | `mutation.isError` | 핵심 개념을 다 찾았어요. |
| StudySummaryPanel 위 (서버 진행) | `summary.status === 'running'` | `summary.status !== 'ready'` | 요약을 다 만들었어요. |
| StudySummaryPanel 아래 (요청) | `mutation.isPending` | `mutation.isError` | 요약을 다 만들었어요. |
| QuizSolvePage 채점 | `mutation.isPending && needsAi` | `mutation.isError` | 없음 (결과 화면으로 이동) |
| QuizResultPage 다시 채점 | `regrade.isPending` | `regrade.isError` | 대기 중이던 답을 다시 채점했어요. |
| QuizCreatePage 만들기 | `mutation.isPending` | `mutation.isError` | 없음 (문제 화면으로 이동) |
| QuizCreatePage 이전 요청 확인 | `pendingKey !== null` | `recoveredMessage !== null` | 없음 |
| AssessmentPage 평가 만들기 | `mutation.isPending` | `mutation.isError` | 평가 문제를 만들었어요. |

  - StudySummaryPanel 위쪽은 지금 3갈래 삼항(`ready ? SummaryView : running ? AiWaiting : Notice`)에서 `AiWaiting` 갈래를 `null`로 바꾸고, 삼항 바로 앞에 `<AiWaitingStatus key={purpose} …>`를 둔다(목적을 바꾸면 새로 그려 다른 목적의 `ready`를 완료로 읽지 않게).
  - `PendingGeneration`은 대기 상자를 그리지 않고 `null`을 돌려주는 확인 전용 컴포넌트로 바꾸고, 그 안내 글("이전에 보낸 퀴즈 만들기 요청을 확인하고 있어요…")은 부모(697행 근처)의 `AiWaitingStatus` children으로 옮긴다(컴포넌트가 사라지면 live 영역도 사라지기 때문). `PendingGeneration` 위 주석도 고친다.
- [ ] **Step 3: `docs/frontend-guide.md` 1절** — `AiWaiting` 설명 뒤에 "화면에는 `AiWaitingStatus`로 둔다(늘 있는 live 영역이라 대기 시작과 완료를 읽는다. 바깥 상자가 이미 live면 `AiWaiting`만)."를 덧붙이고 같은 내용을 다른 곳에 쓰지 않는다.
- [ ] **Step 4: 확인** — `npm run lint && npx tsc -b`. 미리보기 체험 모드에서 자료 상세 → 요약 만들기·개념 찾기, 퀴즈 만들기, 사전평가 만들기, 퀴즈 결과 다시 채점: 대기 상자가 지금과 같은 자리·모양으로 뜨고, 끝난 뒤 빈 자리가 남지 않음(목업이 너무 빨라 대기 상자가 안 보이면 개발자 도구 네트워크 "느린 4G"로). 개발자 도구 Elements에서 `aria-live` 상자가 대기 전·중·후 모두 있음.
- [ ] **Step 5 [사람]:** VoiceOver로 요약 만들기를 누르면 대기 안내를 읽고, 끝나면 "요약을 다 만들었어요."를 읽는지. 실패 시(백엔드 꺼짐 실제 모드 등)에는 오류 문구만 읽는지.

### Task 8: 삭제 뒤 포커스와 알림

**Files:**
- Create: `frontend/src/components/common/ActionNotice.tsx`, `common/index.ts`에 배럴 추가
- Modify: `frontend/src/pages/materials/MaterialListPage.tsx` (`MaterialList` 115-195)
- Modify: `frontend/src/pages/courses/CoursePage.tsx` (`CourseManager` 18-95, 같은 문제)
- Modify: `frontend/src/pages/admin/AdminNoticeListPage.tsx` (같은 문제)

**Interfaces:**
- Produces: `ActionNotice({ notice }: { notice: { id: number; text: string } | null })` — `notice`가 없으면 `null`. 있으면 `<p ref tabIndex={-1} className="text-body text-ink-soft">{text}</p>`를 그리고 `notice.id`가 바뀔 때마다 그 문단에 `focus()`. 포커스로 읽히므로 `role="status"`는 달지 않는다(두 번 읽힘). `id`는 같은 제목을 연달아 지워도 다시 포커스하도록 매번 늘리는 수.

- [ ] **Step 1:** `MaterialList`에 `const [notice, setNotice] = useState<{ id: number; text: string } | null>(null)`. `onConfirm`에서 제목을 잡아 `removal.mutate(deleting.id, { onSuccess: () => setNotice((prev) => ({ id: (prev?.id ?? 0) + 1, text: `'${title}' 자료를 삭제했어요.` })) })`(훅의 `onSuccess`가 돌려준 목록 다시 받기가 끝난 뒤 불린다). `<ActionNotice notice={notice} />`는 `ErrorText` 바로 아래에 두고, 빈 화면(`materials.length === 0`) 분기에서도 `EmptyState` 위에 그린다(마지막 자료를 지운 경우).
- [ ] **Step 2:** `CoursePage`의 `confirmDelete`·`AdminNoticeListPage`의 `onConfirm`에도 같은 방식(문구 "'{이름}' 과목을 삭제했어요.", "'{제목}' 공지를 삭제했어요."). `CoursePage`는 `ErrorText` 아래(빈 목록 분기와 같은 `section` 안이라 한 곳이면 된다).
- [ ] **Step 3:** `npm run lint && npx tsc -b`.
- [ ] **Step 4: 확인** — 체험 모드 키보드만으로: 내 학습자료에서 삭제 버튼(Tab) → Enter → 확인 창 "삭제하기" → 알림 문단에 포커스(테두리 보임), 다음 Tab이 그 아래 첫 자료 링크. 자료를 모두 지워 빈 화면이 돼도 알림에 포커스. 취소하면 포커스가 원래 삭제 버튼으로 돌아감(브라우저 `dialog` 기본). 내 과목도 같은지.
- [ ] **Step 5 [사람]:** VoiceOver로 삭제 뒤 "'…' 자료를 삭제했어요."를 한 번 읽는지.

### Task 9: 메인 스크롤 소개를 화면 낭독기가 모두 읽게

**Files:**
- Create: `frontend/src/pages/main/components/story/storyAnnouncement.ts`
- Test: `frontend/src/pages/main/components/story/storyAnnouncement.test.ts`
- Modify: `frontend/src/pages/main/components/story/ScrollStory.tsx` (`PinnedStory` 54-162)

**Interfaces:**
- Produces: `storyStepText(step: { title: string; headline: string; body: string }, index: number, total: number): string` — `` `${index + 1}단계 · 전체 ${total}단계, ${title}. ${headline의 \n을 공백으로} ${body}` ``. 이 파일은 `storySteps.ts`(장면 컴포넌트를 가져온다)를 import하지 않는다.

- [ ] **Step 1: 실패하는 테스트**

```ts
it('단계 번호·이름·제목·설명을 한 문장으로, 제목의 줄바꿈은 공백으로', () => {
  expect(storyStepText({ title: '대화 가져오기', headline: '흩어진 AI 대화를\n과목별로 모아요', body: '파일을 읽어요.' }, 0, 6))
    .toBe('1단계 · 전체 6단계, 대화 가져오기. 흩어진 AI 대화를 과목별로 모아요 파일을 읽어요.');
});
```

- [ ] **Step 2: 실패 확인** — `npx vitest run src/pages/main/components/story/storyAnnouncement.test.ts` → FAIL.
- [ ] **Step 3: 구현** → 같은 명령 PASS.
- [ ] **Step 4: `PinnedStory`**
  - `{header}` 바로 뒤에 화면 낭독기용 전체 목록 `<ol className="sr-only">`: `STORY_STEPS`마다 `<li>{storyStepText(item, index, TOTAL)}</li>`. 장면 그림은 넣지 않는다(꾸밈 예시라 글로 충분, 좁은 화면 목록과 달리 DOM을 두 배로 늘리지 않게).
  - 눈으로 보는 현재 단계 글 묶음(`min-h-52` div)과 `SceneCard`에 `aria-hidden="true"`(위 목록과 겹쳐 읽히지 않게). 단계 이름 버튼 목록은 그대로 둔다(키보드 사용자가 쓴다).
  - 상태 `announcement`와 `<p aria-live="polite" className="sr-only">`. `goTo(index)`에서만 `setAnnouncement(storyStepText(STORY_STEPS[index], index, TOTAL))`(스크롤로 바뀔 때마다 읽으면 시끄럽다).
- [ ] **Step 5: 확인** — 1280px·동작 줄이기 꺼짐에서 Elements로 sr-only 목록에 6단계가 모두 있고 보이는 글·장면은 `aria-hidden`. 단계 버튼을 키보드로 눌러 장면이 넘어가고 live 문단 글이 바뀜. 좁은 화면·동작 줄이기 켬은 지금과 같음(`StackedStory`).
- [ ] **Step 6 [사람]:** VoiceOver 넓은 화면에서 VO+→로 소개를 읽으면 6단계가 차례로 읽히고, 단계 버튼을 누르면 그 단계 문장을 읽는지.

### Task 10: PR 2 마무리

- [ ] **Step 1:** 전체 검증 명령 통과.
- [ ] **Step 2:** Task 7·8·9 확인 항목을 라이트·다크로 훑고 스크린샷으로 사용자 확인.
- [ ] **Step 3 [사람]:** Task 7 Step 5, Task 8 Step 5, Task 9 Step 6.
- [ ] **Step 4:** 확인 뒤 한 번에 커밋 `fix: AI 대기·삭제·소개 단계를 화면 낭독기가 알리게`. PR 본문: 보이는 화면 변화는 삭제 뒤 알림 문단 한 줄(제출 자료 영상에 삭제 장면이 있으면 갱신).

---

## PR 3 — `fix/a11y-focus-contrast`

### Task 11: 보이는 포커스 표시

**Files:**
- Modify: `frontend/src/pages/analysis/components/AssessmentComparisonPanel.tsx:268`
- Modify: `frontend/src/components/common/fieldClass.ts:3`

**Interfaces:** 없음.

- [ ] **Step 1:** 막대 줄 `li`(`tabIndex={0}`)의 `outline-none`(회귀 PR 뒤 `outline-hidden`이면 그것)을 지워 `index.css`의 공통 `:focus-visible`(2px `primary` 테두리, 2px 띄움)이 보이게 한다. 마우스 클릭으로는 `:focus-visible`이 걸리지 않아 마우스 사용자의 모양은 그대로다.
- [ ] **Step 2: 입력칸 판단** — 회귀 PR 뒤 `FIELD_CLASS`는 `outline-hidden focus:border-primary focus-within:border-primary`다. 1px 회색→파랑 색 변화(2.75:1)는 1.4.11의 3:1에 못 미치므로, `focus-within:ring-1 focus-within:ring-primary`를 더해 포커스 때 테두리를 2px 파랑(바탕 대비 10:1)으로 만든다. 공통 바깥 테두리(2px 띄움) 대신 안쪽 굵기로 하는 이유: 키워드 칩 칸·검색칸처럼 다른 요소에 붙은 칸에서 띄운 테두리가 이웃과 겹친다. `focus-within`은 칸 자신이 포커스돼도 걸리므로 `focus:border-primary`는 지운다.
- [ ] **Step 3: 확인** — 학습 대시보드 사전·사후 비교(목록 보기)에서 Tab으로 막대 줄마다 파란 테두리, 마우스 클릭에는 없음. 회원가입·자료 올리기·과목 키워드 칸·상단바 검색칸·퀴즈 답 칸에서 포커스 때 2px 파란 테두리, 라이트·다크, 칸 높이(44px)가 바뀌지 않음(ring은 자리를 차지하지 않는다).

### Task 12: 라벨 이름·대비·체험 버튼

**Files:**
- Modify: `frontend/src/pages/analysis/components/LayoutReasonPopover.tsx:52-69`
- Modify: `frontend/src/pages/quizzes/QuizCreatePage.tsx:500`
- Modify: `frontend/src/components/common/FileDropZone.tsx:60`
- Modify: `frontend/src/components/common/DemoStartButton.tsx:13-28`

**Interfaces:** 없음.

- [ ] **Step 1 (5번):** 버튼의 `aria-label`을 지우고 보이는 글 뒤에 `<span className="sr-only">: {title}</span>`을 붙인다. 접근 이름이 "이 자리인 이유: 개념별 숙련도"처럼 보이는 글로 시작한다. props 주석("화면 읽기 프로그램용 버튼 이름에 쓴다")은 맞으니 그대로.
- [ ] **Step 2 (9번):** 토큰은 바꾸지 않고 조합을 바꾼다. `ink-muted` 다크 값을 밝히면(`#9a948a`부터 4.5:1) 모든 화면의 `ink-soft`·`ink-muted` 구분(지금 1.2:1 차이)이 더 줄기 때문이다.
  - `QuizCreatePage` 난이도 설명 `span`: 선택된 버튼(`bg-primary-soft`)일 때만 `text-ink-soft`(다크 5.74:1), 아닐 때 `text-ink-muted` 그대로 — `cn()`.
  - `FileDropZone` 설명 `span`: 호버·끌어 놓기 때 바탕이 `primary-soft`가 되므로 늘 `text-ink-soft`.
  - `grep -rn "primary-soft" frontend/src`로 나온 파일에서 같은 요소나 그 자식에 `text-ink-muted`가 있는 곳을 더 찾아 같은 방식으로 고치고, 찾은 곳을 PR 본문에 적는다.
- [ ] **Step 3 (10번):** `authenticated` 대신 `status`를 읽어 `if (!DEMO_AVAILABLE || active || status !== 'anonymous') return null;`. 위 주석에 "세션을 되살리는 중(`unknown`)에도 숨긴다(눌렀다가 로그인과 체험이 함께 켜지지 않게)"를 더한다. 목업 빌드는 `restoreSession`이 첫 렌더 전에 바로 `anonymous`로 바꾸므로 버튼이 늦게 나타나지 않는다.
- [ ] **Step 4: 확인** — 대시보드에서 "이 자리인 이유" 버튼이 Elements 접근성 패널 이름에 보이는 글로 시작. 다크에서 퀴즈 만들기 난이도 선택·자료 올리기 칸 호버의 설명 글 대비를 개발자 도구 색 대비로 4.5:1 이상. `frontend-real-api`(백엔드 없음, 로그인 흔적 있는 브라우저)에서 첫 화면 "로그인 없이 체험해 보기"가 세션 확인이 끝난 뒤에만 보이고, `frontend-mock`에서는 바로 보임.

### Task 13: PR 3 마무리

- [ ] **Step 1:** 전체 검증 명령 통과.
- [ ] **Step 2:** Task 11·12 확인 항목을 라이트·다크로 훑고 스크린샷(입력칸 포커스, 막대 줄 포커스)으로 사용자 확인.
- [ ] **Step 3 [사람]:** VoiceOver로 "이 자리인 이유" 버튼 이름을 읽는지, 음성 제어(설정 > 손쉬운 사용 > 음성 제어)로 "이 자리인 이유 클릭"이 먹는지.
- [ ] **Step 4:** 확인 뒤 한 번에 커밋 `fix: 입력칸·막대 줄 포커스 표시와 대비, 체험 버튼 표시 시점 고치기`. PR 본문에 입력칸 포커스 모양이 바뀌었다고 적는다(제출 영상에 폼 입력 장면이 있으면 갱신 검토).
