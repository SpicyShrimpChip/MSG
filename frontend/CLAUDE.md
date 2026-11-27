# CLAUDE.md — frontend

루트 `CLAUDE.md`의 원칙(미결 사항, 개인정보 처리, 판단 근거 표시)을 먼저 따른다. 화면·기능의 자세한 흐름은 `docs/frontend-guide.md`(아래 "N절"은 이 문서의 절).

## 스택

React 19, TypeScript 6, Vite 8, Tailwind CSS 4, React Router 7(`createBrowserRouter` 배열 라우팅), TanStack Query 5(서버 상태), Zustand 5(클라이언트 UI 상태만), Axios, zod(LLM 출력 검증), Motion(`motion` 패키지)

버전별 주의 (예제·자료가 이전 버전 기준인 경우가 많다):

- **Tailwind 4:** `tailwind.config.js`를 만들지 않는다. 테마 색상·폰트는 CSS `@theme`에 정의하고 Vite 플러그인(`@tailwindcss/vite`)으로 연결한다.
- **React 19:** `forwardRef` 없이 `ref`를 일반 prop으로 받는다. `propTypes`·`defaultProps`는 쓰지 않는다.
- **React Router 7:** v6 데이터 라우터 방식. 프레임워크 모드(파일 기반 라우팅, `react-router.config.ts`)는 쓰지 않는다.

## 명령어

```bash
npm run dev      # 개발 서버 (http://localhost:5173)
npm run lint     # ESLint
npm run format   # Prettier로 형식·클래스 순서 맞추기 (CI 형식 검사가 실패하면 실행)
npm test         # Vitest (src/**/*.test.ts)
npm run build    # 타입 체크 + 빌드
```

작업을 마치면 `npm run lint && npm run format:check && npm test && npm run build`가 통과해야 한다. 테스트는 화면이 아니라 틀리면 안 되는 로직(대화 파싱·마스킹, 점수 계산, API 응답 처리)에 쓴다.

**ESLint가 막는 규칙**(`eslint.config.js`, 이유는 실패 메시지에): `motion.*`(→ `m.*`), `dangerouslySetInnerHTML`, `import.meta.env.VITE_USE_MOCK` 직접 읽기(→ `useDemoStore.ts`), `useMutation` 직접 가져오기(→ `useSingleFlightMutation`), 컴포넌트 파일에서 컴포넌트 아닌 것 내보내기(`react-refresh/only-export-components`). 컴포넌트 파일은 컴포넌트만 내보낸다(개발 중 Fast Refresh가 상태를 유지하도록). 상수·도우미 함수는 같은 폴더의 소문자 파일로(`storySteps.ts`, `consentNotices.ts` 처럼). 파일 예외는 `files` 객체로 해당 항목만 빼고(같은 규칙을 덮어쓰면 옵션이 합쳐지지 않으니 남길 항목을 다시 넘긴다), 한 줄 예외는 `// eslint-disable-next-line <규칙> -- 이유`. 색 코드·`dark:` 클래스처럼 className 안을 봐야 하는 규칙은 오탐이 쉬워 리뷰에서 본다.

## 폴더 구조

`src/`: `api/`(Axios 인스턴스·기능별 API 함수), `components/`(공통 UI·레이아웃·`AsyncBoundary`), `constants/`, `hooks/`(`useCourses`·`useMe` 등), `mocks/`(백엔드 없이 쓰는 샘플 데이터), `pages/`(페이지는 default export, 페이지 전용 컴포넌트는 `pages/<페이지>/components/`), `router/`(라우트·로그인 가드), `store/`(Zustand), `styles/`, `types/`(백엔드 DTO와 같은 모양), `utils/`(`conversation/` 대화 파싱·필터·마스킹, `layout/` 배치 JSON 스키마).

## 코드 규칙

- import는 `@/` 절대경로, 공통 컴포넌트는 배럴(`index.ts`)로 내보낸다.
- 화면은 `components/common`의 공통 부품(1절)을 먼저 쓴다. 같은 클래스 묶음을 세 번째로 쓰면 공통 부품으로 뺀다.
- 조건부 클래스·받은 `className` 합치기는 템플릿 문자열 대신 `cn()`(`utils/cn.ts`, 겹치면 뒤 값이 이김). **`@theme`에 `--text-*`·`--container-*` 토큰을 추가하면 `cn.ts`에도 이름을 넣는다**(빠뜨리면 글자 크기 클래스가 색으로 오인돼 지워진다).
- 클래스 순서는 손으로 맞추지 않는다(`prettier-plugin-tailwindcss`가 `className`·`cn()` 안을 정렬, `TONE_CLASS` 같은 상수 문자열은 제외).
- 서버 데이터를 바꾸는 저장 버튼은 `useSingleFlightMutation`. `disabled={isPending}`는 다시 그린 뒤에야 걸려 연타가 두 번 가므로 `mutate` 직전에 동기 잠금(`utils/submitLock.ts`)을 건다. 서로 겹치면 안 되는 두 버튼은 같은 잠금을 넘기고(`ConceptFeedbackBox`), 자료 올리기는 `requestKey`도 보낸다.
- 서버 데이터는 React Query로만 다루고 Zustand에 복사하지 않는다. 쿼리 키는 문자열로 쓰지 않고 API 파일 상수(`ME_QUERY_KEY` 등)나 훅을 쓴다.
- 로딩·에러는 `useSuspenseQuery` + `AsyncBoundary`. 그 안의 `QueryErrorResetBoundary`를 빼지 않는다(빼면 "다시 시도"가 같은 오류 화면을 남긴다). 서버 404일 수 있는 상세 화면은 `notFoundAction`을 넘긴다(2절).
- 페이지는 `router/index.tsx`에 `lazyPage(() => import('@/pages/...'))`로 추가한다(메인·404만 처음부터). 페이지 모음 파일(`pages/index.ts`)은 만들지 않는다(코드가 쪼개지지 않는다).

## 움직임 (Motion)

- 기준값은 `constants/motion.ts`와 `index.css`의 `--default-transition-*`. 두 곳을 같이 맞추고, 느낌이 어색하면 이 값만 고친다.
- `motion.div` 대신 `motion/react-m`의 `m.div`(`LazyMotion strict`라 번들이 커지고 오류가 난다). 나타나고 사라지는 요소는 `AnimatePresence` + `m.*`, 단순한 색·그림자 전환은 CSS면 충분하다.
- 화면 모드 전환은 View Transitions(3절). 색을 하나씩 섞는 CSS 전환으로 바꾸지 않는다(중간에 글자·바탕 밝기가 같아져 글자가 잠깐 사라진다). `applyTheme`의 `pendingDark`는 지우지 않는다(연타해도 선택과 화면이 어긋나지 않게 하는 값).
- **색 토큰을 추가하면 `styles/theme-transition.css`에도 같은 이름을 `@property`로 등록하고 `transition` 목록에 넣는다**(빠뜨려도 오류는 없지만 폴백에서 그 색만 즉시 바뀐다).
- 화면 모드와 함께 움직여야 하는 요소는 CSS 전환보다 Motion을 쓴다(`theme-switching`이 Motion은 끄지 않는다).
- 미리보기 창이 숨겨져 있으면 View Transition이 시작되지 않는다. 확인할 때는 창을 보이게 하거나 스크린샷을 찍는다.
- "동작 줄이기"에서도 정보를 잃지 않아야 한다(움직임으로만 알려 주지 않는다).

## 백엔드 연동

- 환경변수 `VITE_API_BASE_URL`(기본 `/api`)·`VITE_USE_MOCK`. 팀 공유 값은 `.env.development`(커밋), 내 PC 값은 `.env.development.local`(커밋 안 됨).
- 요청 경로는 항상 `/api/...`(개발은 Vite 프록시가 `localhost:8080`으로, 배포는 Vercel rewrite가 백엔드로). 그래서 백엔드 컨트롤러 경로도 `/api`로 시작한다.
- 배포는 Vercel(`frontend/vercel.json`). 운영 주소 https://lms-seven-tawny.vercel.app/ (목업 모드, main 머지 때 자동 배포, PR마다 미리보기). 빌드 설정·환경변수(`VITE_USE_MOCK`, `HUSKY=0`)는 `docs/deploy.md`.
- **목업 판단은 `store/useDemoStore.ts` 한 곳**(`NO_BACKEND`·`DEMO_AVAILABLE`·`isMockData()`·`useMockData()`, 4절). API 함수는 `if (isMockData()) return mockX()`, 화면은 컴포넌트 맨 위 `const mockData = useMockData()`. 예시: `api/notices.api.ts`.
- 백엔드에 바라는 API(제안)는 `docs/frontend-requests.md`에 모은다. 새 화면에 API가 필요하면 거기에 추가하고, 목업도 같은 모양으로 만든다.
- 공통 응답 `{ success, message, data }`(`ApiResponse<T>`). API 함수는 `api/request.ts`의 `request<T>()`로 부른다(성공하면 `data`만, 실패하면 화면 문구를 담은 `ApiError`를 던진다).

## 로그인·인증 (구조는 4절)

- Access Token은 **메모리에만** 둔다. localStorage에 저장하지 않는다(`msg-has-session` 표시만).
- 동시에 401을 받아도 토큰 갱신은 한 번만 보낸다(Refresh Token은 한 번 쓰면 폐기돼, 두 번 보내면 서버가 재사용으로 보고 세션을 끊는다). `X-Requested-With` 헤더는 CSRF 방어라 지우지 않는다.
- 내 데이터를 보여 주는 새 화면은 `RequireAuth` 아래에, `RequireAdmin`은 반드시 `RequireAuth` 안에 둔다. 가드는 안내용이고 진짜 권한은 서버가 요청마다 확인한다. 내 정보는 스토어가 아니라 `useMe()`로 읽는다.
- 체험 중에는 API 함수가 서버를 부르지 않고(`useDemoStore.test.ts`가 지킨다) 새로고침해도 세션을 되살리지 않는다. 관리자 화면은 체험에서도 열지 않는다.
- `VITE_MOCK_OPEN_ACCESS=true`(`.env.development.local`)는 목업 모드에서 가드를 모두 연다(관리자 포함). **공개 배포에는 넣지 않는다.** 실제 로그인 확인은 `VITE_USE_MOCK=false` + 백엔드(`docs/setup.md`).

## 화면별 규칙 (메뉴·흐름은 5절)

- 메뉴 순서는 학습 흐름(학습자료 → 학습분석 → 맞춤학습 → 이용안내). 현재 위치는 `menuActive.ts`의 `currentMenuPath`가 고른다.
- 커리큘럼 "학습 완료"는 공부한 기록일 뿐 이해 여부·숙련도를 바꾸지 않는다.
- 퀴즈 AI 기능은 `me.aiQuizConsent`(대화 분석 동의와 별도)가 있어야 쓴다. 풀던 답은 탭 sessionStorage(`quizDraft.ts`)에만 둔다.
- 자료 본문 전송은 `ai-status.materialContentEnabled`가 꺼져 있으면 체크를 숨기고 이유를 보여 주고, 켜져 있어도 요청마다 확인 체크를 받는다. 평가 비교에서 채점 대기는 틀린 답으로 세지 않고, 채점 판단이 AI 1개뿐이면 "AI 1개만 판단 (다수결 아님)".
- 공지사항은 메인과 주 메뉴에 두지 않는다. 공지 본문은 글자로만 다룬다(HTML 해석 금지). "관리자" 링크는 `me.role === 'ADMIN'`일 때만.
- 처리방침 링크 이름은 띄어 쓴 "개인정보 처리방침"(작성지침).
- 가입 뒤 돌아갈 화면(`state.from`)은 주소에 넣지 않는다(외부 링크로 엉뚱한 곳에 보낼 수 없게).
- 회원가입: 필수 정보는 동의가 아니라 "개인정보 수집·이용 안내" + **확인** 체크 하나, AI 대화 분석만 **선택 동의**로 따로 두고 처음엔 체크하지 않는다. "가입하면 동의한 것으로 간주" 같은 문구는 쓰지 않는다.

## AI 대화 가져오기 (흐름은 6절)

- 읽은 대화는 이 화면의 메모리에만 두고 서버·브라우저 저장소에 남기지 않는다. 그래서 과목 등록도 이 화면 안에서 하고, 흐름 안에 `/courses`로 가는 링크를 두지 않는다(나가면 파일을 다시 올려야 한다).
- Worker 안의 파일은 `@/utils` 묶음 대신 필요한 파일만 가져온다(화면 코드가 Worker 묶음에 섞이지 않게). 다시 가리는 동안에는 "다음: 보내기"를 막는다(보낼 글이 늘 지금 단어로 가린 결과가 되게).
- **러스트 WASM 빌드 결과(`src/wasm/conversation-core`)는 커밋한다**(Vercel에 러스트가 없다). `wasm/conversation-core`를 고치면 `npm run build:wasm`(rustup `wasm32-unknown-unknown`, `wasm-pack`) 후 같이 커밋한다. CI `npm run check:wasm`이 소스 해시(`SOURCE_HASH`)로 빠뜨림을 잡는다.
- 가리기 규칙을 러스트로 옮길 때는 JS 정규식과 결과를 맞춘다: `\d`·`\b`는 ASCII로 직접 쓰고 `regex`에 없는 앞뒤 확인은 코드로(`fancy_regex`는 40배 넘게 느림, `docs/wiki/frontend.md`).
- **키워드 규칙(영문은 낱말 경계, 한글이 들면 부분 문자열)은 서버 `TermMatch`와 같다. 바꾸면 `filter.ts`·`TermMatch`와 양쪽 테스트 표(`filter.test.ts`·`TermMatchTest`)를 같이 고친다**(다르면 화면에서 고른 대화가 분석에서 빠진다). 형광펜(`marks.ts`)도 같은 규칙.
- 새 서비스는 `utils/conversation/sources/index.ts`에 추가한다(Gemini는 2단계 후보). 공식 형식 문서가 없어 형식이 바뀌면 실제 파일로 다시 확인하고, 읽지 못하면 무엇이 잘못됐는지 알려 준다. **실제 기록 파일은 레포에 넣지 않는다.**
- Claude 목록 파일(`manifest-*.json`)의 zip 링크를 우리 서버가 대신 열지 않는다(claude.ai 로그인이 필요하고 원본이 서버로 오면 안 된다).
- 가릴 단어는 서버에 보내지 않고 localStorage 계정별 키에만 둔다(단어 목록 자체가 개인정보). 읽기·쓰기는 `utils/maskWords.ts` 한 곳(동기화로 바꿀 때 이 파일만 고친다). 동의 철회·탈퇴가 성공하면 지운다.
- AI 답변 마크다운은 `react-markdown`으로 React 요소를 만든다(HTML 문자열 금지, 대화 속 HTML은 그리지 않음). 사용자의 기록이라 AI 말풍선도 보라색으로 칠하지 않는다.
- 체크한 대화만 가린 상태로 보내고, 큰 대화도 메시지를 버리지 않고 조각으로 나눈다.

## 학습 대시보드 (화면 구성은 7절)

- 타입은 백엔드 `AnalysisRun` 그대로(`types/analysis.ts`).
- **모델 비교는 정확도·숙련도가 아니다.** "3개 중 2개 같음"처럼 모델 개수로만 보여 준다(`describeComparison`, 퍼센트 금지). 근거 부족은 이해 부족과 다른 모양(회색 점선, 물음표, `UnderstandingBadge`)으로 그린다. 연결 안 된 모델은 숨기지 않고 "연결 안 됨"으로 보여 준다.
- 모델이 모두 실패한 분석은 이전 결과를 덮지 않고 안내로만 보여 준다.
- 숙련도는 숫자 점수 없이 단계·근거로 보여 준다. 사용자 피드백은 AI 판단을 바꾸지 않는다(추천 순서는 서버가 매긴다).
- 연결된 모델이 없거나 과목 키워드가 없으면 분석 버튼을 막고 이유를 보여 준다. 분석 오류는 서버 `message`를 그대로 보여 준다.
- 목업에서는 화면마다 "예시 데이터"를 붙이지 않고 체험 띠(`DemoBanner`) 하나로 알린다. 예외로 대시보드는 판단 근거 투명성 원칙에 따라 "실제 AI 모델이 판단한 결과가 아니에요"를 한 번 더 둔다.

## 대시보드 동적 배치 (8절)

- 배치 JSON은 `unknown`으로 받아 zod로 검증한 뒤에만 쓰고(`parseDashboardLayout`), 실패하면 **기본 배치**(`DEFAULT_LAYOUT`)로 보여 준다. 거르는 조건은 8절.
- 영역은 모두 한 줄 전체로 위에서 아래로 쌓는다(좌우로 나누면 표 옆이 크게 빈다).
- 이유 말풍선은 호버만 쓰지 않는다(클릭·탭 고정, 키보드로도 뜸). 사용자가 "기본 배치로 보기"로 AI 배치를 끌 수 있어야 한다.
- 새 영역은 `DashboardComponentId` 타입, `DASHBOARD_COMPONENT_IDS`, `DEFAULT_LAYOUT`, `DashboardPage`의 `renderComponent`를 함께 고친다.

## 디자인 (공부 책상: 중립 회색 + 잉크 파랑, 상단바·색 토큰 표는 9절)

- 겉틀(바탕·테두리·상단바)은 조용하게 두고, 색과 공은 내용(숙련도 단계, AI 표시, 형광펜)에 들인다.
- 굵기는 400·500·600만(`font-semibold`가 가장 굵다). 위계는 글자 크기와 색으로 만든다.
- 유리 재질(`styles/glass.css`)은 떠 있는 조작 영역(상단바 알약 등)에만 쓰고, 점수·근거·표처럼 읽는 내용에는 쓰지 않는다.
- 서브페이지(`SubPage`)·폼(`FormRow`) 배치 규칙과 본문 폭·글자 크기 토큰은 9절을 따른다.

### 색 규칙

- 색은 `src/index.css`의 `@theme`에 의미 기반 이름으로 등록한다(Tailwind 기본 팔레트 `blue-600` 등을 덮어쓰지 않게).
- **바탕·넓은 면(큰 상자·푸터)은 중립 회색, 누를 수 있거나 선택된 것과 핵심 숫자만 잉크 파랑(`primary`).** 카드는 흰색에 테두리(`line`). 바탕을 파랑 등 색으로 물들이지 않는다.
- **블루 = 코드가 계산한 값과 일반 UI, 퍼플(`ai`) = AI가 만든 것.** 판단 근거 투명성 원칙을 색으로 보여 주는 장치라 예외 없이 지킨다. 점수·채점 상태·결과 판단·단계 상태·숙련도는 서버 값 그대로 파랑, 해설·피드백·모범 답안·다음 개념 제안처럼 AI가 만든 것만 보라, 자료 원문 근거 문장은 회색 인용. 버튼은 AI 기능이라도 항상 `primary`(필요하면 아이콘만 `ai`).
- **색만으로 의미를 전달하지 않는다.** AI는 반짝이 아이콘 + "AI", 강점은 체크 아이콘 + "강점", 보완 필요는 느낌표 아이콘 + "보완 필요"를 항상 붙인다. AI 요소가 있는 화면에는 "이 표시가 있는 내용은 AI가 작성했어요" 안내를 둔다.
- 보완 필요(`weakness`, 붉은 계열)는 오류처럼 보이지 않게 아이콘·글자를 함께 쓰고 강점(초록)과 밝기를 다르게 둔다(적록색약). `danger`는 오류에만 쓰고 보완 필요에 쓰지 않는다. `highlight`는 가져온 대화의 키워드·검색어 형광펜(이 서비스의 고유 표시)에만. 메인 첫 화면 장면도 같은 형광펜을 보여 주는 것이다.
- **컴포넌트에 색 코드(`text-[#…]`, `bg-white`, `text-white`)를 직접 쓰지 않는다.** `surface`·`ink`·`line` 계열 토큰만. 버튼 글자는 `on-primary`.
- **다크모드:** `src/index.css`의 `:root.dark`에서 같은 토큰을 다시 정의하고, 컴포넌트에 `dark:` 클래스를 붙이지 않는다. 새 토큰은 어두운 값도 넣고 `theme-transition.css`에 등록하고 글자·바탕 대비 4.5:1을 확인한다. 그림자 색은 `shade`(`heading`은 어두운 모드에서 밝아 그림자가 빛처럼 보인다).
- 접근성: 키보드 포커스 표시, 이미지·아이콘 대체 텍스트, 글자 대비 4.5:1 이상, 애니메이션은 `prefers-reduced-motion`을 따른다.

## 문구 규칙

- 사용자 관점의 쉬운 말. 버튼은 실제 동작을 쓴다("자료 올리기", "선택한 대화 3개 보내기").
- 오류는 무엇이 잘못됐고 어떻게 고치는지 쓴다. 빈 화면에는 다음 행동을 안내한다.
