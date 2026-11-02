# CLAUDE.md — frontend

루트 `CLAUDE.md`의 원칙(미결 사항, 개인정보 처리, 판단 근거 표시)을 먼저 따른다.

## 스택

React 19, TypeScript 6, Vite 8, Tailwind CSS 4, React Router 7(`createBrowserRouter` 배열 라우팅),
TanStack Query 5(서버 상태), Zustand 5(클라이언트 UI 상태만), Axios, zod(LLM 출력 검증), Motion(`motion` 패키지, 움직임)

버전별 주의 (예제·자료가 이전 버전 기준인 경우가 많다):

- **Tailwind 4:** `tailwind.config.js`를 만들지 않는다. 테마 색상·폰트는 CSS의 `@theme`에 정의하고, Vite 플러그인(`@tailwindcss/vite`)으로 연결한다.
- **React 19:** `forwardRef` 없이 `ref`를 일반 prop으로 받는다. `propTypes`·`defaultProps`는 쓰지 않는다.
- **React Router 7:** 라우터 정의 방식은 v6 데이터 라우터와 같다. 프레임워크 모드(파일 기반 라우팅, `react-router.config.ts`)는 쓰지 않는다.

## 명령어

```bash
npm run dev      # 개발 서버 (http://localhost:5173)
npm run lint     # ESLint
npm run format   # Prettier로 형식·클래스 순서 맞추기 (CI의 형식 검사가 실패하면 실행)
npm test         # Vitest (src/**/*.test.ts)
npm run build    # 타입 체크 + 빌드
```

작업을 마치면 `npm run lint && npm run format:check && npm test && npm run build`가 통과해야 한다.
테스트는 화면이 아니라 틀리면 안 되는 로직(대화 파싱·마스킹, 점수 계산, API 응답 처리)에 쓴다.

## 폴더 구조 (보일러플레이트 기준)

```
src/
├── api/          Axios 인스턴스·인터셉터, 기능별 API 함수 (materials.api.ts 등)
├── components/   공통 UI, 레이아웃(Header·GNB·Footer), AsyncBoundary, Chatbot
├── constants/    메뉴 구성, 라벨 매핑 등 상수
├── hooks/        공통 커스텀 훅 (`useCourses`: 내 과목 목록, `useMe`: 로그인했을 때 내 정보)
├── mocks/        백엔드 없이 개발할 때 쓰는 샘플 데이터
├── pages/        페이지(default export). 페이지 전용 컴포넌트는 pages/<페이지>/components/ 에 둔다
├── router/       라우트 정의, 로그인 가드
├── store/        Zustand 스토어 (인증 세션 등)
├── styles/       전역 CSS, Tailwind 설정 연동
├── types/        타입 정의. 백엔드 DTO와 같은 모양으로 맞춘다
└── utils/        conversation/(대화 파싱·필터·마스킹), layout/(배치 JSON 스키마·렌더러) 등
```

- import는 `@/` 절대경로를 쓰고, 공통 컴포넌트는 배럴(`index.ts`)로 내보낸다.
- 화면을 만들 때 공통 부품을 먼저 쓴다: 버튼 `Button`, 흰 카드 `Card`, 작은 알약 `Chip`, 점수·비중 막대 `ScoreBar`(개념 막대는 `level`을 넘기면 보완 필요가 붉게), 파일 끌어다 놓기 `FileDropZone`, 회색 안내 상자(아이콘+글) `Notice`, 입력칸 `TextInput`·`SelectInput`(그 밖의 칸 모양은 `FIELD_CLASS`), 폼 줄과 오류 문구 `FormRow`(`error`), AI 글 `AiNote`·`AiBadge`, 강점·보완 `LevelBadge`. 같은 클래스 묶음을 세 번째로 쓰게 되면 공통 부품으로 뺀다.
- 클래스를 조건에 따라 붙이거나 부품이 받은 `className`과 합칠 때는 템플릿 문자열 대신 `cn()`(`utils/cn.ts`)을 쓴다. 겹치는 클래스는 뒤에 넘긴 값이 이긴다. `@theme`에 `--text-*`·`--container-*` 토큰을 추가하면 `cn.ts`에도 이름을 넣는다(빠뜨리면 글자 크기 클래스가 색으로 오인돼 지워진다).
- 클래스 순서는 손으로 맞추지 않는다. `prettier-plugin-tailwindcss`가 저장·커밋할 때 `className`과 `cn()` 안의 클래스를 Tailwind 권장 순서로 정렬한다(`.prettierrc`). `TONE_CLASS` 같은 상수 문자열은 정렬 대상이 아니다.
- 서버 데이터는 React Query로만 다루고 Zustand에 복사하지 않는다. 쿼리 키는 문자열로 직접 쓰지 않고 API 파일의 상수(`ME_QUERY_KEY`, `COURSES_QUERY_KEY`)나 훅을 쓴다.
- 로딩·에러 처리는 `useSuspenseQuery` + `AsyncBoundary`로 한다. 불러오는 동안은 `LoadingFallback`(회색 틀 `Skeleton`)이 보인다.
- 페이지는 처음 방문할 때만 코드를 불러온다. `router/index.tsx`에 `lazyPage(() => import('@/pages/...'))`로 추가하고, 페이지 파일은 default export로 만든다(메인·404만 처음부터 불러온다). 여러 페이지를 한 번에 export하는 모음 파일(`pages/index.ts`)은 만들지 않는다. 쪼개지지 않는다.

## 움직임 (Motion)

- 기준값은 `constants/motion.ts`(시간·곡선·`FADE_UP`)와 `index.css`의 `--default-transition-*`에 모아 둔다. 두 곳의 값은 같이 맞춘다. 느낌이 어색하면 이 값만 고친다.
- `main.tsx`에서 `LazyMotion`(필요한 기능만, `strict`)과 `MotionConfig reducedMotion="user"`로 감쌌다. 그래서 컴포넌트에서는 `motion.div` 대신 **`import * as m from 'motion/react-m'`의 `m.div`**를 쓴다. `motion.div`를 쓰면 번들이 커지고 `strict`에서 오류가 난다.
- 나타나고 사라지는 요소(펼침 패널, 말풍선, 결과 문구)는 `AnimatePresence` + `m.*`. 단순한 색·그림자 전환은 CSS(`index.css`가 링크·버튼·입력칸에 기본으로 걸어 둔다)로 충분하다.
- 페이지 전환은 `PageTransition`(본문 페이드인), 코드 불러오는 동안은 `NavigationProgress`(화면 위 진행 막대).
- 스크롤에 따라 장면이 바뀌는 구간(메인 스크롤 소개): 긴 구간 안에 `sticky` 무대를 두고 `useScroll`의 진행도로 단계를 넘긴다. 장면 전환 곡선은 `SWEEP_TRANSITION`. 장면 안 요소는 `SceneMotion`의 `Reveal`·`MaskSwap`·`SlotItem`으로 차례로 움직이고, 좁은 화면·"동작 줄이기"에서는 `SceneMotionContext`를 꺼서 고정 없이 세로로 쌓고 마지막 모습만 보여 준다
- 화면 모드를 바꿀 때는 **View Transitions**로 이전 화면과 새 화면을 겹쳐 서서히 바꾼다(`useThemeStore`의 `applyTheme`, 시간 350ms·곡선 `cubic-bezier(0.4, 0, 0.2, 1)`, `--theme-transition-*`). 색을 하나씩 섞는 방식(CSS 전환)은 쓰지 않는다: 글자색과 배경색이 서로 반대로 뒤집히는 전환을 색별로 섞으면 중간에 두 밝기가 같아져(대비 1.5~2.5까지 떨어짐) 글자가 잠깐 사라졌다 나타나 컷편집처럼 보였다. 전환하는 동안 `theme-vt` 클래스가 색 토큰·요소 전환을 모두 끈다.
- 연달아 눌러도 선택과 화면이 어긋나지 않도록 `applyTheme`은 `<html>`의 `dark` 클래스 대신 아직 적용되지 않은 최신 목표(`pendingDark`)를 먼저 본다. 이 값을 지우지 않는다.
- View Transitions를 지원하지 않는 브라우저의 폴백: 색 토큰(`--color-*`)을 `src/styles/theme-transition.css`에서 `@property`(`<color>` 타입)로 등록하고 `:root`에서 `transition`을 걸어 토큰을 쓰는 모든 요소가 함께 서서히 따라오게 한다. 그동안 `theme-switching` 클래스(400ms)가 요소별 전환을 꺼서 한 박자씩 늦게 따라오지 않게 한다. "동작 줄이기"를 켠 기기는 두 방식 모두 쓰지 않고 바로 바뀐다.
- **색 토큰을 추가하면 `styles/theme-transition.css`에도 같은 이름을 등록하고 `transition` 목록에 넣는다**(폴백용). 빠뜨려도 오류는 없지만 폴백에서 그 색만 서서히 안 바뀌고 즉시 바뀐다. 이 파일의 색 부분은 `index.css`의 `@theme` 값에서 만든 것이다(초기값은 라이트 값).
- 미리보기 창이 숨겨져 있으면 프레임이 안 그려져서 View Transition이 시작되지 않는다(화면 모드가 안 바뀐 것처럼 보임). 개발 중 확인할 때는 창을 보이게 하거나 스크린샷을 찍어 프레임을 만든다.
- Motion으로 움직이는 요소(`ThemeToggle`의 해·달 아이콘)는 CSS 전환이 아니라서 `theme-switching`의 영향을 받지 않는다. 같은 이유로, 화면 모드와 함께 움직여야 하는 요소는 CSS 전환보다 Motion을 쓴다.
- "동작 줄이기"를 켠 기기에서는 CSS 움직임이 사실상 꺼지고(`index.css`), Motion은 이동·회전(transform) 움직임을 건너뛴다. 새 움직임도 이 설정에서 정보를 잃지 않아야 한다(움직임으로만 알려 주지 않는다).

## 백엔드 연동

- 환경변수: `VITE_API_BASE_URL`(기본 `/api`), `VITE_USE_MOCK`. 팀 공유 값은 `.env.development`(커밋), 내 PC에서만 바꿀 값은 `.env.development.local`(커밋 안 됨)
- 요청 경로는 항상 `/api/...`. 개발 중에는 Vite 프록시가 `localhost:8080`으로, 배포 때는 Vercel rewrite가 백엔드로 넘긴다. 그래서 백엔드 컨트롤러 경로도 `/api`로 시작한다
- 배포는 Vercel(`frontend/vercel.json`, 모든 경로를 `index.html`로). 운영 주소 https://lms-seven-tawny.vercel.app/ (목업 모드, main 머지 때 자동 배포, PR마다 미리보기). 빌드 설정·환경변수(`VITE_USE_MOCK`, `HUSKY=0`)는 `docs/deploy.md`
- `VITE_USE_MOCK=true`면 API 함수가 `mocks/` 데이터를 반환한다. 백엔드가 준비된 API부터 하나씩 실제 호출로 바꾼다. 예시: `api/notices.api.ts`
- 백엔드에 바라는 API(제안)는 `docs/frontend-requests.md`에 모은다. 새 화면에서 API가 필요해지면 거기에 추가하고, 목업도 같은 모양으로 만든다
- 백엔드 공통 응답 형식: `{ success: boolean, message: string, data: T }` (`types/api.ts`의 `ApiResponse<T>`)
- API 함수는 `api/request.ts`의 `request<T>()`로 요청한다. 성공하면 `data`만 돌려주고, 실패하면 화면에 보여 줄 문구를 담은 `ApiError`를 던진다(`AsyncBoundary`가 받는다)

## 로그인·인증 구조 (주미 인증 API 연결)

- **로그인 상태**는 `store/useAuthStore.ts`(Zustand)가 든다. `status`는 `unknown`(새로고침 뒤 세션 복원 중) / `authenticated` / `anonymous`이고, Access Token은 **메모리에만** 둔다(localStorage에 저장하지 않는다). localStorage에는 "로그인한 적 있음" 표시(`ai-lms-has-session`)만 남겨, 새로고침 뒤 갱신 요청을 보낼지 정한다.
- **새로고침 복원:** `main.tsx`가 `restoreSession()`(`api/auth.api.ts`)을 불러 HttpOnly 쿠키의 Refresh Token으로 Access Token을 다시 받는다.
- **토큰 갱신(`api/axios.ts`):** 요청 인터셉터가 `Authorization: Bearer`를 붙인다. 401이 오면 `refreshAccessToken()`으로 한 번 갱신하고 원래 요청을 다시 보낸다. 여러 요청이 동시에 401을 받아도 갱신은 한 번만 보낸다(Refresh Token은 한 번 쓰면 폐기돼서, 두 번 보내면 서버가 재사용으로 보고 세션을 끊는다). `/auth/*` 요청은 갱신하지 않고, 갱신이 실패하면 `clearSession()`으로 로그아웃 상태가 된다. `X-Requested-With` 헤더는 CSRF 방어라 지우지 않는다.
- **화면 가드:** `router/RequireAuth.tsx`는 로그인이 필요한 화면을 감싼다(`unknown`이면 로딩, `anonymous`면 `/login`으로 보내고 `state.from`으로 원래 화면에 돌아온다). `router/RequireAdmin.tsx`는 반드시 `RequireAuth` 안에 두고 `useMe()`의 `role`이 `ADMIN`이 아니면 안내 화면을 보여 준다. 가드는 안내용일 뿐이고 진짜 권한은 서버가 요청마다 확인한다. 내 데이터를 보여 주는 새 화면은 `RequireAuth` 아래에 넣는다.
- **내 정보**는 스토어가 아니라 `hooks/useMe.ts`(React Query `['me']`)로 읽고, 로그인했을 때만 불러온다.
- 목업 모드(`VITE_USE_MOCK=true`)에서는 두 가드가 검사를 건너뛴다. 실제 로그인으로 확인하려면 `.env.development.local`에 `VITE_USE_MOCK=false`를 넣고 백엔드를 켠다(`docs/setup.md`).

## 주요 화면 (GNB 5개 + 드롭다운)

- 학습자료: 내 학습자료(목록), 자료 업로드, 자료 상세(탭: 요약 / 핵심 개념 / 퀴즈 / AI 요약 영상-준비 중)
- 맞춤학습: 맞춤 커리큘럼(단계별 "이 순서인 이유" 표시), 퀴즈(목록·풀기·결과)
- 학습분석: 학습 대시보드(학습 분석 API, 배치 JSON 틀), AI 대화 가져오기
- 커뮤니티: 공지사항(목록·상세)
- 관리자(`/admin/notices`·`new`·`:id/edit`, 관리자만): 공지 작성·수정·삭제. `RequireAdmin`(`RequireAuth` 안)은 안내용 가드이고 진짜 권한은 서버가 `/api/admin/**`에서 매번 확인한다. 상단바 "관리자" 링크는 `me.role === 'ADMIN'`일 때만 보인다. 목업 모드에서는 가드를 건너뛰고(주소로 직접 접속), 공지는 브라우저(`ai-lms-mock-notices`)에만 저장된다. 공지 본문은 글자로만 다룬다(HTML 해석 금지)
- 이용안내: 서비스 소개, 개인정보 처리방침(작성지침에 따라 링크 이름은 띄어 쓴 "개인정보 처리방침", 푸터에서 굵게)
- 메인(`/`): 지금은 비로그인 소개 하나 — 파란 띠 → 바로가기 4칸 → 스크롤 5단계 소개(`pages/main/components/story/`) → 시작 안내(`StartPaths`) → 개인정보 안내. 로그인 홈(가입 직후·기록 쌓는 중·기록 있음)은 아직 없음(흐름은 정해 둠)
- 그 외: 로그인, 회원가입(비밀번호 4조건 실시간 표시), 마이페이지(동의 철회·데이터 삭제, 대화 가져올 때 가릴 단어), 검색
- 가입 뒤 이동: 로그인 가드나 시작 안내가 `state.from`(가려던 화면)을 넘기면 회원가입 → 로그인 화면이 그대로 이어 받아, 로그인 뒤 그 화면으로 보낸다. 주소에 넣지 않아 외부 링크로 엉뚱한 곳에 보낼 수 없고, 새로고침하면 사라져 메인으로 간다
- 회원가입 동의(`SignupAgreements`, 「개인정보 처리 통합 안내서」 2025.7. Ⅳ 기준): 서비스에 꼭 필요한 정보는 필수 동의가 아니라 "개인정보 수집·이용 안내"(항목·목적·근거·보유 기간)를 보여 주고 **확인** 체크 하나(이용약관 포함)로 받는다. AI 대화 분석만 **선택 동의**로 따로 두고 목적·항목·보유 기간·거부해도 되는 점을 함께 알리며, 처음엔 체크하지 않은 상태로 둔다. "가입하면 동의한 것으로 간주" 같은 문구는 쓰지 않는다

## AI 대화 가져오기 (브라우저 처리)

화면은 `pages/analysis/ConversationImportPage.tsx`(4단계: 파일 고르기 → 공부한 대화 고르기 → 가린 내용 확인 → 보내기), 단계별 부품은 `pages/analysis/components/import/`. 읽은 대화는 이 화면의 메모리에만 두고 서버·브라우저 저장소에 남기지 않는다.

- **파일 읽기:** 내보낸 zip을 압축 풀지 않고 받는다. `utils/conversation/archive.ts`가 zip을 `Blob.slice`로 필요한 부분만 읽는다(3GB zip도 통째로 메모리에 올리지 않는다). `zipReader.ts`가 끝의 중앙 목차(ZIP64 포함)를 읽어 대화 기록 파일(`conversations*.json`)만 꺼내 풀고(fflate는 deflate 해제만, CRC 확인, 푼 크기 합계 상한 300MB), 목차가 없는 잘린 zip은 앞에서부터 로컬 헤더를 따라가며 건지고 "파일이 잘려 있어요" 안내를 낸다. 잘린 ChatGPT zip(3.5GB, 대화 527개)을 2026-11-02에 이 방식으로 읽는 것까지 확인했다. 크기를 데이터 뒤에 적는 zip(data descriptor)은 아직 못 읽는다. 대화 없이 알려진 파일(`user.json`, `conversation_asset_file_names.json` 등)은 이름으로 알아보고 조용히 건너뛴다(회색 안내 `MergedImport.notices`). 압축을 푼 `conversations.json`도 받는다. 여러 파일을 고르면 `merge.ts`가 같은 대화를 하나로 합친다
- **서비스별 구조:** `utils/conversation/sources/`(`chatgpt.ts`, `claude.ts`)에 서비스마다 파일을 알아보고 읽는 방법을 둔다. 새 서비스는 `sources/index.ts` 목록에 추가한다(Gemini는 2단계 후보). Claude는 2026-10-31, ChatGPT는 2026-11-01 실제 내보내기 파일로 확인했다. ChatGPT 답변의 출처·위젯 표시(`\ue200종류\ue202인자\ue201`)는 파서가 지우고 이름·링크 글자만 남긴다. 공식 형식 문서가 없으니 형식이 바뀌면 실제 파일로 다시 확인한다
- **Claude 목록 파일:** Claude 내보내기는 먼저 "다운로드 목록 파일"(`manifest-....json`, 대화 없음)을 준다. `manifest.ts`가 알아보고 안의 zip 링크만 보여 준다. 링크를 우리 서버가 대신 열지 않는다(claude.ai 로그인이 필요하고 원본이 서버로 오면 안 된다)
- **고르기:** `assign.ts`가 내 과목 키워드로 공부한 대화를 과목별로 나누고, `search.ts`가 본문 검색·정렬을 한다(모두 브라우저 안)
- **가리기:** `mask.ts`가 정규식(이메일·전화번호·주민번호·카드번호·키·IP)과 내 이름(`[이름]`), 사용자가 넣은 단어(`[가린 단어]`)를 가린다. 3단계에서 가린 곳 목록과 원래 글자(이 화면에서만)를 보여 준다
- **가릴 단어 저장:** 마이페이지와 3단계 "이 브라우저에 기억하기"로 저장한 단어는 서버에 보내지 않고 localStorage에 계정별 키(`ai-lms-mask-words:<id>`)로만 둔다(단어 목록 자체가 개인정보). 읽기·쓰기는 `utils/maskWords.ts` 한 곳에 모으고 화면은 `useSavedMaskWords` 훅으로 쓴다. 기기 간 동기화로 바꿀 때는 이 파일만 고친다. 동의 철회·탈퇴가 성공하면 지운다
- **대화 보기:** `ConversationView.tsx`가 2·3단계에서 대화를 채팅창 모양으로 보여 준다. AI 답변 마크다운은 `react-markdown`으로 React 요소를 만들고(HTML 문자열 금지, 대화 속 HTML은 그리지 않음), 요소 모양은 `markdownComponents.tsx`(링크는 새 탭, 다른 사이트 이미지는 불러오지 않음), 가린 표시(파랑)·키워드 형광펜은 `marks.ts`. 3단계 가린 곳 목록의 "보기"는 그 줄만 원래 글자를 보여 주고(위 "원래 글자 보기"는 전체), 본문의 그 가린 표시 하나를 CSS Custom Highlight(`::highlight(mask-focus)`)로 진하게 칠하고 스크롤한다(`focus.markIndex` = `MaskedSpot.labelIndex`, 코드 블록 안도 칠한다. 본문은 서버로 가는 모습 그대로 `[IP]`처럼 보인다). 사용자의 기록이라 AI 말풍선도 보라색으로 칠하지 않는다
- **보내기:** 사용자가 체크한 대화만 가린 상태로 보낸다. `send.ts`가 과목마다, 요청 크기(1MB, 임시값)와 대화 수(50개)에 맞춰 나누고, 혼자 큰 대화는 메시지를 버리지 않고 조각으로 나눈다(`docs/frontend-requests.md` "대화 가져오기")
- 내보내기 형식은 바뀔 수 있으므로 실제 파일로 검증하고, 읽지 못하면 무엇이 잘못됐는지 알려 준다. 실제 기록 파일은 레포에 넣지 않는다

## 학습 대시보드 (학습 분석 API, 11/2)

- `/analysis`는 과목을 골라 백엔드 학습 분석 API(`api/analysis.api.ts`: `providers`, `runs/latest`(없으면 404 → `null`), `runs` 시작)로 그린다. 타입은 백엔드 `AnalysisRun` 그대로(`types/analysis.ts`). GPT·Claude·Gemini가 따로 판단한 결과를 개념마다 비교해 보여 준다.
- **비교는 정확도·숙련도가 아니다.** "2개 모두 같음", "3개 중 2개 같음"처럼 모델 개수로만 보여 준다(`describeComparison`, 퍼센트 금지). 판단 근거 부족은 이해 부족과 다른 모양(회색 점선, 물음표)으로 그린다(`UnderstandingBadge`). 연결 안 된 모델은 "연결 안 됨"으로 숨기지 않고 보여 준다.
- 모델이 하나도 연결되지 않았거나 과목 키워드가 없으면 분석 버튼을 막고 이유를 보여 준다. 분석 오류는 서버 `message`를 그대로 보여 준다. 목업에서는 "예시 데이터예요"를 표시한다.
- 숙련도 점수 막대·방사형 그래프·과목별 비중은 점수 산식(미결)이 정해지면 다시 붙인다.
- "이 판단, 내 생각과 맞나요?"(`ConceptFeedbackBox`, 모델별 판단 아래): 이미 알아요 / 더 공부할래요 / 선택 취소를 피드백 API(`PUT·DELETE`)로 저장하고 최근 분석을 다시 불러온다. 내 선택은 AI 판단을 바꾸지 않고 "더 공부할래요"만 추천 순서 맨 앞으로 옮긴다(순서는 서버가 매긴다). 확인 퀴즈는 퀴즈 만들기(AI 생성) API가 생기면 붙인다(풀기·채점 API는 #107로 있음).

## 대시보드 동적 배치

- 배치 JSON은 `unknown`으로 받아 zod 스키마로 검증한 뒤에만 사용한다 (`utils/layout/dashboardLayout.ts`의 `parseDashboardLayout`). **지금 백엔드는 배치를 주지 않아(`AnalysisRun.layout` 없음) 실제 화면은 기본 배치이고, 목업에만 예시 배치가 있다.**
- 허용 영역 id: `summary`(분석 요약), `conceptTable`(개념별 판단 표), `conceptDetail`(모델별 판단·근거), `models`(모델 연결 상태), `recommendation`(추천 학습 순서). 크기는 `wide`(한 줄 전체) / `normal`(넓은 화면에서 반 칸).
- 없는 id, 같은 영역·같은 순서 번호 중복, 80자를 넘거나 빈 이유, 형식이 다른 숫자는 검증에서 거르고 **기본 배치**(`DEFAULT_LAYOUT`)로 보여 준다. 배치에서 빠진 영역은 기본 순서로 뒤에 붙인다.
- 각 영역 제목 오른쪽 "이 자리인 이유" 말풍선(AI, 보라색)으로 이유를 보여 준다. 마우스를 올리거나 키보드로 가면 뜨고, 클릭·탭하면 고정, Esc·바깥 클릭으로 닫힌다(휴대폰·키보드 사용자도 볼 수 있게 호버만 쓰지 않는다). 그리고 맨 위 한 줄 띠(`LayoutSummary`)에 전체 이유와 **배치할 때 본 숫자**(코드, 파란색)를 나란히 보여 주고, **"기본 배치로 보기"**로 사용자가 AI 배치를 끌 수 있다.
- "PDF로 받기"는 브라우저 인쇄(`window.print()`)를 연다. 상단바·푸터·버튼은 `print:hidden`으로 인쇄에서 뺀다.
- 새 영역을 추가하려면 `DashboardComponentId` 타입, `DASHBOARD_COMPONENT_IDS`, `DEFAULT_LAYOUT`, `DashboardPage`의 `renderComponent`를 함께 고친다.

## 디자인 (푸른 계열, 시안 v3 확정 2026-10-27)

시안: Claude 디자인 캔버스 "AI LMS 현대화 시안"(메인, 학습 분석 배치 A·B, 자료 올리기), "AI LMS 메인 홈 시안"(스크롤 소개, 글라스 비교). 10/26 회의에서 기관형 LMS 톤(두 줄 네이비 GNB, 상아 바탕, 가운데 제목)을 "푸른 계열"로 바꾸기로 했다. 대회 심사에 디자인 배점이 없으므로 겉틀은 단순하게 두고, AI가 보이는 화면에 공을 들인다.

- 상단바 한 줄(흰 바탕): 왼쪽 로고 → 주 메뉴(선택된 메뉴는 파란 글자 + 아래 3px 선, 호버 드롭다운) → 오른쪽 검색·계정(로그인 글자 + 회원가입 버튼)·화면 모드 버튼(해·달 하나). 1024px 미만은 주 메뉴 대신 햄버거 메뉴(`MobileMenu`, 흰 패널). 1024px 이상에서는 위에 붙어 따라오고, 맨 위에서 8px 넘게 내려가면 가운데로 모인 반투명 알약(유리 효과)이 된다. 알약 폭은 `width:auto`가 전환되지 않아 로고·메뉴 칸·오른쪽 묶음 너비를 더해 CSS 변수 `--capsule`로 넣는다(`useCapsule.ts`, 여백 값은 Header 클래스와 맞춘다). 알약일 때 검색칸은 돋보기 버튼으로 접히고 누르면 펼쳐진다(알약 안에 테두리 상자를 두지 않는다)
- 유리 재질(`styles/glass.css`)은 내용 위에 떠 있는 조작 영역(상단바 알약 등)에만 쓰고, 점수·근거·표처럼 읽는 내용에는 쓰지 않는다. 굴절은 `.glass-frame` + `GlassSurface`: SVG 필터가 CPU로 그려져 넓게 걸면 스크롤이 느려지므로 휘는 가장자리 조각에만 걸고 가운데는 흐림만 둔다. 크롬·엣지만 굴절, 다른 브라우저는 흐림만, "투명도 줄이기"는 단색. 스크롤 중에만 모습이 바뀌는 식의 끄고 켜기는 하지 않는다
- 서브페이지(`SubPage`): 왼쪽 정렬 큰 제목·부제. `HOME > 경로`는 쓰지 않는다(현재 위치는 주 메뉴 선택 표시로 알려 준다)
- 폼(`FormRow`): 위에 라벨, 아래 입력칸(높이 44px)을 세로로 쌓는다. 표 형식(회색 라벨 칸)은 쓰지 않는다. 파일 고르기는 끌어다 놓는 영역 + 숨긴 입력칸
- 공지사항은 메인에 두지 않고 커뮤니티 메뉴에만 둔다

### 색 규칙

Tailwind 4 `@theme`에 아래 이름으로 등록한다(`src/index.css`). Tailwind 기본 팔레트(`blue-600` 등)를 덮어쓰지 않도록 의미 기반 이름을 쓴다.

| 토큰                                               | 값                                                 | 쓰는 곳                                                                  |
| -------------------------------------------------- | -------------------------------------------------- | ------------------------------------------------------------------------ |
| `brand` / `on-brand` / `on-brand-hover`            | `#133b9e` / `#ffffff` / `#c3d3fb`                  | 메인 첫 화면 띠처럼 넓은 짙은 파랑 면과 그 위 글자                       |
| `heading`                                          | `#0f1b33`                                          | 페이지 제목, 카드 제목                                                   |
| `heading-soft`                                     | `#1e3a8a`                                          | 섹션 제목, 표 상단선                                                     |
| `primary` / `on-primary` / `primary-hover`         | `#1d4ed8` / `#ffffff` / `#1e40af`                  | 모든 버튼·글자 링크, 선택된 메뉴, 로고, 숙련도 점수 막대                 |
| `primary-soft`                                     | `#eaf1ff`                                          | 작은 강조: 아이콘 상자, 선택된 단계·버튼                                 |
| `primary-pill`                                     | `#dbe6ff`                                          | 알약형 보조 버튼                                                         |
| `surface` / `surface-raised` / `surface-muted`     | `#ffffff` / `#ffffff` / `#f4f7fd`                  | 사이트 바탕 / 카드·입력칸·드롭다운 / 표 머리·연한 칸·막대 바탕           |
| `hero` / `section`                                 | `#f4f7fd` / `#f4f7fd`                              | 메인 첫 화면 띠 / 큰 안내 상자(시작 안내, 서비스 소개 상자)              |
| `footer`                                           | `#eaf1ff`                                          | 푸터 배경 (글자는 `heading`)                                             |
| `line` / `line-strong`                             | `#e3e9f3` / `#c9d3e3`                              | 구분선, 카드 테두리                                                      |
| `input-border`                                     | `#7c8598`                                          | 입력칸·검색칸 테두리 (바탕 대비 3:1 이상)                                |
| `ai` / `ai-soft` / `ai-border` / `ai-chip`         | `#6d28d9` / `#f8f5ff` / `#ddd2fb` / `#f3effe`      | AI가 만든 콘텐츠만                                                       |
| `highlight` / `on-highlight`                       | `#fde68a` / `#3f2d00` (다크 `#6b5310` / `#fff3c4`) | 가져온 대화에서 찾은 키워드·검색어 형광펜만 (가린 표시는 `primary-soft`) |
| `strength` / `strength-soft`                       | `#15803d` / `#e7f6ec`                              | 강점 표시                                                                |
| `weakness` / `weakness-soft` / `weakness-bar`      | `#c0392b` / `#fdecea` / `#e0574a`                  | 보완 필요 표시 / 그 바탕 / 보완 필요 개념의 점수 막대                    |
| `danger`                                           | `#b91c1c`                                          | 오류 메시지만                                                            |
| `overlay`                                          | `#0f1b33` (다크 `#000000`)                         | 확인 창 뒤를 덮는 막 (`backdrop:bg-overlay/50`)                          |
| `window-close` / `window-minimize` / `window-zoom` | `#ff5f57` / `#febc2e` / `#28c840` (다크 같음)      | 스크롤 소개 장면 창 틀의 맥 창 버튼 점 (꾸밈용, 글자에 쓰지 않음)        |

- **바탕은 흰색, 넓은 면은 옅은 파랑, 강조는 파랑.** 바탕은 흰색, 넓은 면(띠, 큰 상자, 푸터)은 옅은 파랑(`surface-muted`·`section`·`footer`)으로 나누고, 누를 수 있거나 선택된 것만 파랑(`primary` 계열)으로 칠한다. 카드는 흰색에 테두리(`line`)로 구분한다.

- **블루 = 코드가 계산한 값과 일반 UI, 퍼플(`ai`) = AI가 만든 것.** 판단 근거 투명성 원칙을 색으로 보여 주는 장치이므로 예외 없이 지킨다.
  - `ai` 색: AI 설명, 판단 근거 요약, AI 추천 학습 순서와 "이 순서인 이유", 챗봇 답변, 대시보드 "왜 여기에 있나요?"
  - 버튼은 AI 기능을 실행하더라도 항상 `primary`. 필요하면 아이콘만 `ai`로 붙인다.
  - 숙련도 점수·그래프는 코드가 계산한 값이므로 `primary`. `ai`로 칠하지 않는다.
- **색만으로 의미를 전달하지 않는다.** AI 요소에는 반짝이 아이콘과 "AI" 글자를, 강점에는 체크 아이콘과 "강점", 보완 필요에는 느낌표 아이콘과 "보완 필요"를 항상 함께 붙인다. AI 요소가 있는 화면에는 "이 표시가 있는 내용은 AI가 작성했어요" 안내를 둔다.
- 보완 필요는 붉은 계열(`weakness`, 10/27 결정: 주황보다 눈에 띄게)이지만 오류처럼 보이지 않도록 항상 느낌표 아이콘과 "보완 필요" 글자를 함께 쓰고, 강점(초록)과는 밝기도 다르게 둔다(적록색약). 오류 빨강(`danger`)은 파싱 실패·입력 오류 같은 오류에만 쓰고 보완 필요에 쓰지 않는다.
- **컴포넌트에 색 코드(`text-[#…]`, `bg-white`, `text-white`)를 직접 쓰지 않는다.** 바탕·글자·구분선도 `surface`, `ink`, `line` 계열 토큰만 쓴다. 버튼 글자는 `on-primary`, 짙은 파랑 띠(`brand`) 위 글자는 `on-brand`.
- **다크모드:** 표의 값은 밝은 모드 기준이고, 어두운 값은 `src/index.css`의 `:root.dark`에서 같은 토큰을 다시 정의한다. 컴포넌트에 `dark:` 클래스를 붙이지 않는다. 새 토큰을 추가하면 어두운 값도 함께 넣고(그리고 `styles/theme-transition.css`에 등록하고), 글자·바탕 쌍의 대비 4.5:1을 확인한다. 기본값은 기기 설정을 따르고, 상단바의 화면 모드 버튼 하나(`ThemeToggle`, 누르면 지금 보이는 모드의 반대로 바뀜, `useThemeStore`)로 바꾼다. 어두운 값은 시안 v3를 어둡게 옮긴 남색 기 바탕(`surface` `#0f141e` < 카드 `#161d2b` < 연한 칸 `#1c2434`)에 밝은 파랑(`#7aa5f5`)이다. 그림자 색은 `heading` 대신 `brand`를 쓴다(어두운 모드에서 `heading`은 밝아서 그림자가 빛처럼 보인다).
- 페이지 본문 폭은 `max-w-page`(1200px), 자주 쓰는 글자 크기는 `text-body`(15px)·`text-caption`(13px)·`text-display`(40px) 토큰을 쓴다.

- 폰트: Pretendard (대체: Noto Sans KR, 시스템 폰트)
- 접근성: 키보드 포커스 표시, 이미지·아이콘 대체 텍스트, 글자 대비 4.5:1 이상, 애니메이션은 `prefers-reduced-motion`을 따른다

## 문구 규칙

- 사용자 관점의 쉬운 말. 버튼은 실제 동작을 쓴다("자료 올리기", "선택한 대화 3개 보내기").
- 오류는 무엇이 잘못됐고 어떻게 고치는지 쓴다. 빈 화면에는 다음 행동을 안내한다.
