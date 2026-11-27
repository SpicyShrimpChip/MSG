# 프론트엔드 기능 안내

`frontend/CLAUDE.md`에서 옮긴 자세한 흐름이다. 지켜야 할 규칙은 `frontend/CLAUDE.md`에 있고, 이 문서는 화면·기능이 어떻게 동작하는지를 적는다. 코드와 다르면 코드를 기준으로 이 문서를 고친다.

## 1. 공통 부품

화면을 만들 때 공통 부품을 먼저 쓴다: 버튼 `Button`, 흰 카드 `Card`, 작은 알약 `Chip`, 점수·비중 막대 `ScoreBar`(개념 막대는 `level`을 넘기면 보완 필요가 붉게), 금방 끝나는 저장을 기다리는 동안 도는 아이콘 `Spinner`, AI 응답을 오래 기다리는 안내 상자 `AiWaiting`(점 구슬 + 글, `thinking-orbs`를 처음 뜰 때 받는다. 세 모델 분석은 `weaving`, 그 밖의 AI 작업은 `connecting`, 아이콘 자리에 구슬만 넣을 때는 `AiOrb`), 파일 끌어다 놓기 `FileDropZone`, 회색 안내 상자(아이콘+글) `Notice`, 입력칸 `TextInput`·`SelectInput`(그 밖의 칸 모양은 `FIELD_CLASS`), 폼 줄과 오류 문구 `FormRow`(`error`), AI 글 `AiNote`·`AiBadge`(화면의 "AI 표시가 있는 내용은…" 안내 한 줄은 `AiLegend`), 강점·보완 `LevelBadge`.

## 2. 로딩·오류 화면

불러오는 동안은 `LoadingFallback`(회색 틀 `Skeleton`)이 보인다. `AsyncBoundary`는 `QueryErrorResetBoundary`로 감싸 "다시 시도"가 실패한 조회를 다시 부르게 한다. 상세 화면처럼 없거나 남의 것(서버 404)일 수 있는 화면은 `notFoundAction`(예: `{ label: '퀴즈 목록으로', to: '/quizzes' }`)을 넘겨, 404면 "다시 시도" 대신 목록으로 가는 버튼을 보여 준다. 화면을 그리다 난 오류와 배포 뒤 옛 화면 코드 불러오기 실패는 라우터 루트의 `RouteErrorPage`가 받는다(코드 불러오기 실패는 30초 안에 한 번만 자동 새로고침).

## 3. 움직임

- `main.tsx`에서 `LazyMotion`(필요한 기능만, `strict`)과 `MotionConfig reducedMotion="user"`로 감쌌다.
- 단순한 색·그림자 전환은 `index.css`가 링크·버튼·입력칸에 기본으로 걸어 둔다.
- 페이지 전환은 `PageTransition`(본문 페이드인), 코드 불러오는 동안은 `NavigationProgress`(화면 위 진행 막대).
- 스크롤에 따라 장면이 바뀌는 구간(메인 스크롤 소개): 긴 구간 안에 `sticky` 무대를 두고 `useScroll`의 진행도로 단계를 넘긴다. 장면 전환 곡선은 `SWEEP_TRANSITION`. 장면은 가짜 브라우저 틀(창 점·주소줄) 없이 흰 카드에 화면 이름표만 달고(`SceneCard`), 왼쪽에는 작은 단계 표시·짧은 제목·한두 줄 설명과 지금 단계만 잉크 선으로 짚는 단계 이름 목록을 둔다. 장면 안 요소는 `SceneMotion`의 `Reveal`·`MaskSwap`·`SlotItem`으로 차례로 움직이고, 좁은 화면·"동작 줄이기"에서는 `SceneMotionContext`를 꺼서 고정 없이 세로로 쌓고 마지막 모습만 보여 준다.
- 화면 모드를 바꿀 때는 **View Transitions**로 이전 화면과 새 화면을 겹쳐 서서히 바꾼다(`useThemeStore`의 `applyTheme`, 시간 350ms·곡선 `cubic-bezier(0.4, 0, 0.2, 1)`, `--theme-transition-*`). 색을 하나씩 섞는 방식(CSS 전환)은 쓰지 않는다: 글자색과 배경색이 서로 반대로 뒤집히는 전환을 색별로 섞으면 중간에 두 밝기가 같아져(대비 1.5~2.5까지 떨어짐) 글자가 잠깐 사라졌다 나타나 컷편집처럼 보였다. 전환하는 동안 `theme-vt` 클래스가 색 토큰·요소 전환을 모두 끈다.
- View Transitions를 지원하지 않는 브라우저의 폴백: 색 토큰(`--color-*`)을 `src/styles/theme-transition.css`에서 `@property`(`<color>` 타입)로 등록하고 `:root`에서 `transition`을 걸어 토큰을 쓰는 모든 요소가 함께 서서히 따라오게 한다. 그동안 `theme-switching` 클래스(400ms)가 요소별 전환을 꺼서 한 박자씩 늦게 따라오지 않게 한다. "동작 줄이기"를 켠 기기는 두 방식 모두 쓰지 않고 바로 바뀐다. 이 파일의 색 부분은 `index.css`의 `@theme` 값에서 만든 것이다(초기값은 라이트 값).
- Motion으로 움직이는 요소(`ThemeToggle`의 해·달 아이콘)는 CSS 전환이 아니라서 `theme-switching`의 영향을 받지 않는다.
- "동작 줄이기"를 켠 기기에서는 CSS 움직임이 사실상 꺼지고(`index.css`), Motion은 이동·회전(transform) 움직임을 건너뛴다.

## 4. 로그인·인증 구조

- **로그인 상태**는 `store/useAuthStore.ts`(Zustand)가 든다. `status`는 `unknown`(새로고침 뒤 세션 복원 중) / `authenticated` / `anonymous`이다. localStorage에는 "로그인한 적 있음" 표시(`msg-has-session`)만 남겨, 새로고침 뒤 갱신 요청을 보낼지 정한다.
- **새로고침 복원:** `main.tsx`가 `restoreSession()`(`api/auth.api.ts`)을 불러 HttpOnly 쿠키의 Refresh Token으로 Access Token을 다시 받는다.
- **토큰 갱신(`api/axios.ts`):** 요청 인터셉터가 `Authorization: Bearer`를 붙인다. 401이 오면 `refreshAccessToken()`으로 한 번 갱신하고 원래 요청을 다시 보낸다. 여러 요청이 동시에 401을 받아도 갱신은 한 번만 보낸다. 탭끼리는 Web Locks(`navigator.locks`, 이름 `msg-auth-refresh`)로 차례를 지키고, Web Locks가 없으면 그냥 보낸다(서버가 10초 유예로 받아 준다). `/auth/*` 요청은 갱신하지 않고, 갱신이 실패하면 `clearSession()`으로 로그아웃 상태가 된다.
- **화면 가드:** `router/RequireAuth.tsx`는 로그인이 필요한 화면을 감싼다(`unknown`이면 로딩, `anonymous`면 `/login`으로 보내고 `state.from`으로 원래 화면에 돌아온다). `router/RequireAdmin.tsx`는 `useMe()`의 `role`이 `ADMIN`이 아니면 안내 화면을 보여 준다.
- **내 정보**는 `hooks/useMe.ts`(React Query `['me']`)로 읽고, 로그인했을 때만 불러온다.

### 체험 모드

- `store/useDemoStore.ts`가 세 가지를 구분한다: `NO_BACKEND`(백엔드 없음: 로그인 가드·세션 복원·로그인 닫힘 안내·Turnstile), `DEMO_AVAILABLE`(체험 버튼을 켤 수 있음, 실제 서버 빌드에서도 true), `isMockData()`·`useMockData()`(지금 데이터를 `mocks/`에서 읽는지 = 백엔드가 없거나 체험 중). API 함수의 `isMockData()`는 React 밖이라 부르는 순간의 값이다.
- 목업 모드(`VITE_USE_MOCK=true`, Vercel 배포)에는 로그인이 없어서 "체험 모드"로 가드를 통과한다. `store/useDemoStore.ts`의 `active`(탭 `sessionStorage`)가 켜져 있어야 `RequireAuth`가 화면을 열고, 아니면 `/login`으로 보낸다.
- 켜는 곳은 `DemoStartButton`("로그인 없이 체험해 보기": 메인 첫 화면·시작 안내·로그인/가입 화면의 안내 상자 `MockAuthNotice`)이고, 체험 중에는 `DemoBanner`(`Layout` 맨 위 띠, 상단바와 한 묶음으로 화면 위에 고정)가 "예시 데이터"라고 알리며 "체험 끝내기"로 끈다.
- 체험을 시작·끝낼 때 목업 기록(`msg-mock-*`, 공지 제외)을 지우고, 체험 중에는 계정 메뉴를 숨기며 마이페이지는 "체험에서는 보여 드리지 않아요" 안내를 보여 준다. 끝낼 때는 가드가 먼저 `/login`으로 보내 버리지 않게 `window.location.assign('/')`로 메인을 새로 불러온다.
- 체험 중 만든 퀴즈(`msg-mock-created-quizzes`)도 새로고침해도 남고, 새 퀴즈 id는 가장 큰 id 다음이라 앞 퀴즈 결과를 이어받지 않는다.
- 예시 분석·사전사후평가 비교는 정보보호개론(과목 1)만 있고, 다른 과목은 "예시 결과가 없어요"·"아직 사전평가가 없어요"로 보여 준다(다른 과목에 정보보호 개념을 보여 주지 않는다). 관리자 화면(`RequireAdmin`)은 체험에서도 열지 않는다.
- **실제 서버 모드(`VITE_USE_MOCK=false`)에서도 체험을 켤 수 있다(주소 하나로 체험·실제 서비스):** 로그인하지 않았을 때만 체험 버튼이 보이고(로그인·가입 화면 안내 상자에도), 시작할 때 React Query 캐시를 비운다. 체험 중에는 가드가 로그인 없이 화면을 열고, API 함수는 `isMockData()`로 서버를 부르지 않으며(`store/useDemoStore.test.ts`가 지킨다), 새로고침해도 세션을 되살리지 않는다. 체험 중에 로그인하면 `login()`이 체험을 끝내고 그때부터 내 데이터를 읽는다.

## 5. 주요 화면 (GNB 4개 + 드롭다운)

메뉴 순서는 학습 흐름을 따른다: 학습자료(모으기: 과목·자료·AI 대화 가져오기) → 학습분석(진단: 대시보드) → 맞춤학습(공부·확인: 커리큘럼·사전·사후평가·퀴즈) → 이용안내. 주소와 메뉴 묶음이 다를 수 있어서(`/analysis/import`는 학습자료) 현재 위치는 `components/layout/menuActive.ts`의 `currentMenuPath`가 가장 구체적인 경로 하나로 고른다.

- 학습자료: 내 학습자료(목록), 자료 업로드, 자료 상세(탭: 요약 / 핵심 개념 / 퀴즈 / AI 요약 영상-준비 중)
- 맞춤학습: 맞춤 커리큘럼(`/curriculum?courseId=`, `GET /api/curriculum/courses/{id}`의 `flow`: STEP 1 사전 테스트 → 2 핵심 개념 학습("개념 보기" → 자료 상세 `?tab=concepts&concept=`, 개념 카드의 "학습 완료"는 `POST /api/materials/{id}/concepts/progress`, 진행률은 `flow.progress`. 자료 개념이 없고 키워드 개념뿐이면 카드에 "퀴즈로 확인하기"(→ `/quizzes/new?courseId=&concept=`)가 뜨고, 진도는 사전 테스트를 낸 뒤 채점된 키워드 퀴즈 수로 센다) → 3 개념별 확인 퀴즈(`/quizzes/new?checkMaterialId=&checkConceptId=`, 결과로 이해 확인·복습 필요·추가 확인 필요) → 4 사후 평가. 학습 완료는 공부했다는 기록일 뿐 이해 여부·숙련도를 바꾸지 않는다. 단계 상태·요약은 서버 규칙이라 파랑, 기존 개념별 순서·"이 순서인 이유"는 접힌 "복습 우선순위와 근거"에, AI 다음 개념 제안만 보라), 퀴즈(목록·만들기 `/quizzes/new`·풀기·결과, 모의시험 선택). 만들기 요청 키는 탭 sessionStorage에 두고 새로 고치면 `GET /api/quizzes/generations/{key}`로 결과를 찾는다. 퀴즈 AI 기능은 `me.aiQuizConsent`(AI 퀴즈 처리 동의, 대화 분석 동의와 별도)가 있어야 쓸 수 있다. 학습 모드 3가지(시험 대비·자기개발·업무 활용)와 객관식·주관식·서술형. 풀던 답은 탭의 sessionStorage(`quizDraft.ts`)에만 두고, 점수·채점 상태·결과 판단은 서버 값 그대로(파랑), 해설·피드백·모범 답안·다음 개념 제안만 AI(보라)로 보여 준다
- 사전·사후평가(`/assessments`): 과목에 연결된 자료를 골라 사전평가 → 공부 → 사후평가(같은 개념·유형·문항 수·난이도). 평가할 개념은 자료에서 뽑은 개념(`MaterialConceptPanel`, 자료 상세 "핵심 개념" 탭과 공용: 근거 문장은 자료 원문이라 회색 인용, AI 설명만 보라), 없으면 과목 키워드. 자료 본문 전송은 서버 설정(`ai-status.materialContentEnabled`, 기본 꺼짐)이 꺼져 있으면 체크를 숨기고 이유를 보여 준다. 켜져 있어도 요청마다 확인 체크가 있어야 한다. 대시보드 아래쪽 `AssessmentComparisonPanel`(맨 위 요약 칸의 "자세히"가 내려오는 곳)이 사전(주황 `chart-pre`)·사후(파랑 `chart-post`) 막대, %p 변화, 오른 개념·아직 부족한 개념(복습 퀴즈 링크), 비교 주의 문장을 서버 값 그대로 보여 준다(채점 대기는 틀린 답으로 세지 않음). 채점 판단이 AI 1개뿐이면 "AI 1개만 판단 (다수결 아님)"
- 학습분석: 학습 대시보드(학습 분석 API, 배치 JSON 틀). AI 대화 가져오기는 주소는 `/analysis/import`지만 메뉴는 학습자료
- 공지사항(목록·상세): 커뮤니티 메뉴는 멘토링에서 뺐다(AI 기능이 없어 서비스가 산만해 보이고, 익명성을 지키는 서비스 정체성과 맞지 않는다). 화면·API는 남아 있고, 관리자 공지 관리와 검색 결과에서만 연다
- 관리자(`/admin/notices`·`new`·`:id/edit`, 관리자만): 공지 작성·수정·삭제. 상단바 "관리자" 링크는 `me.role === 'ADMIN'`일 때만 보인다. 목업 모드에서는 체험 모드에서도 열지 않고(개발 중 `VITE_MOCK_OPEN_ACCESS=true`만 예외), 그때 공지는 브라우저(`msg-mock-notices`)에만 저장된다
- 이용안내: 서비스 소개, 개인정보 처리방침(작성지침에 따라 링크 이름은 띄어 쓴 "개인정보 처리방침", 푸터에서 굵게)
- 메인(`/`): 지금은 비로그인 소개 하나 — 첫 화면(왼쪽 소개 문구, 오른쪽 "내 질문 → 키워드 형광펜 → 개념 단계" 예시 장면) → 바로가기 4칸(구분선 한 줄) → 스크롤 5단계 소개(`pages/main/components/story/`) → 시작 안내(`StartPaths`) → 개인정보 안내. 로그인 홈(가입 직후·기록 쌓는 중·기록 있음)은 아직 없음(흐름은 정해 둠)
- 그 외: 로그인, 회원가입(비밀번호 4조건 실시간 표시, `VITE_TURNSTILE_SITE_KEY`가 있고 실제 모드면 Cloudflare Turnstile 자동 가입 방지 `TurnstileWidget`: 확인 전엔 가입 버튼을 막고, 가입이 실패하면 토큰을 새로 받는다. 처리방침 7·8·14번에 적었다), 마이페이지(동의 철회·데이터 삭제, 대화 가져올 때 가릴 단어), 검색
- 가입 뒤 이동: 로그인 가드나 시작 안내가 `state.from`(가려던 화면)을 넘기면 회원가입 → 로그인 화면이 그대로 이어 받아, 로그인 뒤 그 화면으로 보낸다. 주소에 넣지 않아 외부 링크로 엉뚱한 곳에 보낼 수 없고, 새로고침하면 사라져 메인으로 간다
- 회원가입 동의(`SignupAgreements`, 「개인정보 처리 통합 안내서」 2025.7. Ⅳ 기준): 서비스에 꼭 필요한 정보는 필수 동의가 아니라 "개인정보 수집·이용 안내"(항목·목적·근거·보유 기간)를 보여 주고 **확인** 체크 하나(이용약관 포함)로 받는다. AI 대화 분석만 **선택 동의**로 따로 두고 목적·항목·보유 기간·거부해도 되는 점을 함께 알리며, 처음엔 체크하지 않은 상태로 둔다

## 6. AI 대화 가져오기 (브라우저 처리)

화면은 `pages/analysis/ConversationImportPage.tsx`(4단계: 파일 고르기 → 공부한 대화 고르기 → 가린 내용 확인 → 보내기), 단계별 부품은 `pages/analysis/components/import/`.

- **Web Worker:** 파일 읽기(zip 풀기·파싱, `utils/conversation/readExportFiles.ts`)와 가리기(가린 곳 목록 포함, `maskConversationWithSpots`)는 대화 처리 Worker(`utils/conversation/worker/`)에서 한다. 화면은 `readExportFilesOffThread`·`maskConversationsOffThread`(가리기 단계는 `useMaskedConversations`)만 부르고, Worker를 만들 수 없거나 깨지면 같은 함수를 화면 스레드에서 부른다.
- **러스트 WebAssembly(`wasm/conversation-core`):** zip 항목 압축 풀기·CRC 확인과 개인정보 가리기는 러스트 크레이트를 WASM으로 빌드해 Worker에서 부른다(`utils/conversation/core.ts`, 쓰기 전에 `loadConversationCore()`). JSON 파싱은 브라우저 `JSON.parse`(이미 네이티브)를 그대로 쓴다. 위치는 UTF-16 단위로 돌려준다. 테스트는 `src/test/setupWasm.ts`가 WASM을 먼저 초기화한다. zip 열기에 쓰던 fflate는 이제 테스트에서 zip을 만들 때만 쓴다(개발 의존성). 측정(글 344만 자·zip 52MB, Node): 압축 풀기+CRC는 예전 JS(fflate)보다 2.3배 빠르고, 가리기는 V8 정규식과 비슷하거나 조금 느리다
- **파일 읽기:** 내보낸 zip을 압축 풀지 않고 받는다. `utils/conversation/archive.ts`가 zip을 `Blob.slice`로 필요한 부분만 읽는다(3GB zip도 통째로 메모리에 올리지 않는다). `zipReader.ts`가 끝의 중앙 목차(ZIP64 포함)를 읽어 대화 기록 파일(`conversations*.json`)만 꺼내 풀고(deflate 해제·CRC 확인은 러스트 WASM, 푼 크기 합계 상한 300MB), 목차가 없는 잘린 zip은 앞에서부터 로컬 헤더를 따라가며 건지고 "파일이 잘려 있어요" 안내를 낸다. 잘린 ChatGPT zip(3.5GB, 대화 527개)을 이 방식으로 읽는 것까지 확인했다. 크기를 데이터 뒤에 적는 zip(data descriptor)은 아직 못 읽는다. 대화 없이 알려진 파일(`user.json`, `conversation_asset_file_names.json` 등)은 이름으로 알아보고 조용히 건너뛴다(회색 안내 `MergedImport.notices`). 압축을 푼 `conversations.json`도 받는다. 여러 파일을 고르면 `merge.ts`가 같은 대화를 하나로 합친다
- **서비스별 구조:** `utils/conversation/sources/`(`chatgpt.ts`, `claude.ts`)에 서비스마다 파일을 알아보고 읽는 방법을 둔다. Claude·ChatGPT 모두 실제 내보내기 파일로 확인했다. ChatGPT 답변의 출처·위젯 표시(`종류인자`)는 파서가 지우고 이름·링크 글자만 남긴다
- **Claude 목록 파일:** Claude 내보내기는 먼저 "다운로드 목록 파일"(`manifest-....json`, 대화 없음)을 준다. `manifest.ts`가 알아보고 안의 zip 링크만 보여 준다
- **고르기:** `assign.ts`가 내 과목 키워드로 공부한 대화를 과목별로 나누고, `search.ts`가 본문 검색·정렬을 한다(모두 브라우저 안). 키워드는 대소문자를 무시하고, **영문 키워드(ASCII 영문·숫자와 공백·`./+#&-`로만 된 것)는 낱말 경계에서만** 찾는다(앞뒤가 ASCII 영문·숫자가 아니어야 함: "ship"의 "IP"는 안 걸리고 "IP를"은 걸림). 한글이 든 키워드는 부분 문자열이다(`filter.ts`). 검색어는 부분 문자열 그대로다
- **과목 등록:** 키워드 있는 과목이 없으면 파일 고르기 전에 과목 폼(`CourseGate` → `CourseQuickForm`, 내 과목 화면의 `CourseForm` 재사용)을 먼저 보여 주고, 2단계의 "과목·키워드 더하기"도 그 자리에서 연다. 키워드를 더하면 읽은 대화를 다시 나누고, 새로 걸린 대화는 선택된 상태로 들어간다
- **가리기:** 규칙(이메일·전화번호·주민번호·카드번호·키·IP, 내 이름 `[이름]`, 사용자가 넣은 단어 `[가린 단어]`)은 러스트 WASM(`wasm/conversation-core/src/mask.rs`)에 있고, `mask.ts`는 그것을 불러 가린 곳 목록을 만든다. 3단계에서 가린 곳 목록과 원래 글자(이 화면에서만)를 보여 준다
- **가릴 단어 저장:** 마이페이지와 3단계 "이 브라우저에 기억하기"로 저장한 단어는 localStorage에 계정별 키(`msg-mask-words:<id>`)로만 둔다. 화면은 `useSavedMaskWords` 훅으로 쓴다. 동의 철회·탈퇴가 성공하면 지운다
- **대화 보기:** `ConversationView.tsx`가 2·3단계에서 대화를 채팅창 모양으로 보여 준다. AI 답변 마크다운은 `react-markdown`으로 React 요소를 만들고, 요소 모양은 `markdownComponents.tsx`(링크는 새 탭, 다른 사이트 이미지는 불러오지 않음), 가린 표시(파랑)·키워드 형광펜은 `marks.ts`(학습 키워드는 고른 규칙과 같이 낱말 경계로, 검색어는 부분 문자열로 칠한다). 3단계 가린 곳 목록의 "보기"는 그 줄만 원래 글자를 보여 주고(위 "원래 글자 보기"는 전체), 본문의 그 가린 표시 하나를 CSS Custom Highlight(`::highlight(mask-focus)`)로 진하게 칠하고 스크롤한다(`focus.markIndex` = `MaskedSpot.labelIndex`, 코드 블록 안도 칠한다. 본문은 서버로 가는 모습 그대로 `[IP]`처럼 보인다)
- **보내기:** 사용자가 체크한 대화만 가린 상태로 보낸다. `send.ts`가 과목마다, 요청 크기(1MB, 임시값)와 대화 수(50개)에 맞춰 나누고, 혼자 큰 대화는 메시지를 버리지 않고 조각으로 나눈다(`docs/frontend-requests.md` "대화 가져오기")

## 7. 학습 대시보드

- `/analysis`는 과목을 골라 백엔드 학습 분석 API(`api/analysis.api.ts`: `providers`, `runs/latest`(`LatestAnalysis`: 모델이 판단한 최근 결과 `run` + 그 뒤의 진행 중·실패한 시도 `lastAttempt`, 없으면 404 → 둘 다 `null`), `runs` 시작)로 그린다. 타입은 백엔드 `AnalysisRun` 그대로(`types/analysis.ts`). 모델이 모두 실패한 분석은 이전 결과를 덮지 않고 안내(`FailedAttemptNotice`)로만 보여 주고, 서버가 분석 중이라고 하면(새로고침해도) `AiWaiting`을 띄우고 버튼을 막은 채 끝날 때까지 다시 확인한다. GPT·Claude·Gemini가 따로 판단한 결과를 개념마다 비교해 보여 준다.
- **화면 구성:** 위에서 아래로 공부할 순서대로 본 개념 목록(`ConceptList`, 가장 중요해서 맨 위) → 요약 칸 4개(`SummaryStrip`: 숙련도 분포·모델 의견 개수·사전→사후평가·학습 진도, 상자 없이 얇은 세로선으로 나눔) → 모델 연결 상태·분석 요약 → 사전·사후평가 비교·학습 진도 자세히. 개념 한 줄에 순서·이름·숙련도·모델 판단 표식(`ModelVerdictMark`: GPT·Claude·Gemini 같은 크기 칸 세 개에 판단 아이콘, 아래 작은 모델 이름. 알약 대신 칸을 나란히 두어 같음·갈림이 모양으로 보인다)을 두고, 누르면 그 자리에서 왼쪽은 코드 값(숙련도 근거·변화·내 피드백), 오른쪽은 AI(추천 공부 방법·모델별 판단과 근거 `ConceptJudgments`)가 펼쳐진다. 처음엔 1번 개념만 펼친다. 새 분석이 도착하면 영역마다 테두리 빛이 한 바퀴 돈다(`ResultBeam`).
- **숙련도(코드 계산, `concepts[].mastery`):** 개념 줄의 숙련도(`MasteryMeter`: 세 칸 막대 + 아이콘 + 근거 부족·보완 필요·보통·강점)와 펼친 칸의 근거(`MasteryEvidence`: 반영한 퀴즈 문제 수·얻은 점수 비율, 대화 수·모델 판단, 근거 부족이면 `quizNeeded`로 "퀴즈 N문제 더"). 그 아래 "이 개념의 변화"(`MasteryTrend`: 단계×날짜 점 그림 최근 6개 + 기록 목록, 문장은 `utils/mastery.ts`)와 바로 앞 기록 대비 "한 단계 올랐어요"(근거 부족·산식 변경이 끼면 표시 안 함). 방사형 그래프·과목별 비중은 아직 없다.
- "이 판단, 내 생각과 맞나요?"(`ConceptFeedbackBox`, 펼친 칸 왼쪽 숙련도 근거 아래): 이미 알아요 / 더 공부할래요 / 선택 취소를 피드백 API(`PUT·DELETE`)로 저장하고 최근 분석을 다시 불러온다. 내 선택은 AI 판단을 바꾸지 않고 "더 공부할래요"만 추천 순서 맨 앞으로 옮긴다(순서는 서버가 매긴다). "이미 알아요"를 고르면 "확인 퀴즈 만들기"(`/quizzes/new?mode=self_development&concept=`)로 가고, 그 뒤에 푼 결과(`concepts[].quiz.confirmation`, 서버 코드 규칙)를 같은 상자에 보여 준다.

## 8. 대시보드 동적 배치

- 배치 검증(`parseDashboardLayout`)은 없는 id, 같은 영역·같은 순서 번호 중복, 80자를 넘거나 빈 이유, 형식이 다른 숫자를 거르고 기본 배치(`DEFAULT_LAYOUT`)로 보여 준다. 배치에서 빠진 영역은 기본 순서로 뒤에 붙인다.
- 지금 백엔드는 배치를 주지 않아(`AnalysisRun.layout` 없음) 실제 화면은 기본 배치이고, 목업에만 예시 배치가 있다.
- 허용 영역 id: `summary`(요약 칸 `SummaryStrip`), `concepts`(개념 목록 `ConceptList`), `models`(모델 연결 상태 + 분석 요약). 영역은 모두 한 줄 전체로 위에서 아래로 쌓는다(예전처럼 표와 상세를 좌우로 나누면 상세가 길어 표 옆이 크게 비고, 시선이 좌우로 오갔다). `size`는 스키마에 남아 있지만 화면에서는 쓰지 않는다.
- 각 영역 제목 오른쪽 "이 자리인 이유" 말풍선(AI, 보라색)으로 이유를 보여 준다. 마우스를 올리거나 키보드로 가면 뜨고, 클릭·탭하면 고정, Esc·바깥 클릭으로 닫힌다. 그리고 맨 위 한 줄 띠(`LayoutSummary`)에 전체 이유와 **배치할 때 본 숫자**(코드, 파란색)를 나란히 보여 주고, **"기본 배치로 보기"**로 사용자가 AI 배치를 끌 수 있다.
- "PDF로 받기"는 브라우저 인쇄(`window.print()`)를 연다. 상단바·푸터·버튼은 `print:hidden`으로 인쇄에서 뺀다.

## 9. 디자인 (공부 책상: 중립 회색 + 잉크 파랑)

방향은 "공부 책상"이다: 꼼꼼하고 차분하고 조금 다정하게. 바탕·테두리·글자는 아주 옅게 따뜻한 중립 회색이고, 색은 내용(숙련도 단계, AI 표시, 형광펜)에 쓴다. 강조색은 잉크 파랑 하나(초록=강점, 붉은색=보완 필요, 보라=AI, 노랑=형광펜이라 남는 계열이 파랑이다)로 버튼·선택 표시·핵심 숫자에만 쓴다. 처음 시안은 Claude 디자인 캔버스 "AI LMS 현대화 시안"·"AI LMS 메인 홈 시안"이다.

- 굵기는 400·500·600만 쓴다(`font-semibold`가 가장 굵다). 위계는 굵기가 아니라 글자 크기와 색으로 만든다.
- 알약(둥근 바탕 칩)은 상태 하나를 짚을 때만 쓰고, 개수·메타 정보는 조용한 글자로 쓴다. 그림자는 중립 먹색(`shade`)으로 작게, 파란 그림자는 쓰지 않는다.

- 상단바 한 줄: 왼쪽 로고 → 주 메뉴(선택된 메뉴는 연한 파랑 알약 + 파란 글자, 호버 드롭다운. 알약은 하나뿐이라 마우스·키보드로 옮긴 메뉴로 미끄러져 가고 벗어나면 제자리로 돌아온다(`Gnb`가 칸을 재서 움직임). 알약의 왼쪽·오른쪽 끝을 따로 움직여(가는 쪽 앞 끝은 빠른 스프링, 뒤 끝은 느린 스프링 `PILL_*_SPRING`) 늘어났다 끌려오듯 줄고, 늘어난 만큼 높이를 살짝 줄인다. 필터 없음) → 오른쪽 검색·계정(로그인 글자 + 회원가입 버튼)·화면 모드 버튼(해·달 하나). 1024px 미만은 주 메뉴 대신 햄버거 메뉴(`MobileMenu`, 흰 패널)이고, 이때도 흰 상단바가 위에 붙어 따라온다(메뉴가 화면보다 길면 패널 안에서 스크롤). 1024px 이상에서는 본문 폭(1152px)의 흰 알약(64px, 거의 불투명한 바탕 + 흐림)이 위에 떠 있다.
- **아래로 내리면 상단바가 숨고, 조금이라도 위로 올리면 스프링으로 다시 나온다(`useHideOnScroll`, 학습 화면을 읽는 동안 자리를 비워 둔다).** 맨 위 근처, 키보드 포커스·마우스가 상단바 안에 있을 때, 햄버거 메뉴·휴대폰 검색칸이 열려 있을 때는 숨기지 않는다. 체험 모드 띠는 숨지 않고 상단바가 그 뒤로 빠진다. 숨은 동안 빈 자리는 클릭을 통과시킨다(`Layout`의 묶음 `pointer-events-none`). 그래서 스크롤 때 높이 줄이기·검색칸 접기는 없앴고, 검색칸은 넓은 화면에서 늘 펼쳐 둔다(휴대폰 폭만 돋보기).
- 메뉴는 로고 옆(48px)에 붙이고 검색·계정은 오른쪽 끝에 둔다(메뉴를 정가운데에 두면 넓은 오른쪽 묶음 때문에 오른쪽으로 쏠려 보였다). 상단바 바깥 자리(h-20)는 고정이라 아래 내용이 들썩이지 않는다
- 유리 재질 굴절(SVG 필터 `GlassSurface`)은 뺐다: 밝은 바탕에서는 보이지 않고, 예전 첫 화면 파란 띠 위에서는 알약이 연보라 판이 됐는데 코드·스크롤 비용·크롬 오류만 남았다. 지금은 `.glass`(흐림 + 그림자)에 상단바만 바탕을 `bg-surface-raised/90`으로 덮는다. "투명도 줄이기"는 단색

- 서브페이지(`SubPage`): 왼쪽 정렬 큰 제목·부제. `HOME > 경로`는 쓰지 않는다(현재 위치는 주 메뉴 선택 표시로 알려 준다). 폼·글 위주 화면은 `narrow`(`form` 896px·`reading` 1024px)를 넘겨 제목과 본문을 같은 폭으로 가운데에 둔다(넓은 창에서 왼쪽으로 쏠리지 않게). 화면 안쪽에는 `max-w-*`를 따로 두지 않는다. 표·카드 목록 화면은 넘기지 않고 전체 폭
- 폼(`FormRow`): 위에 라벨, 아래 입력칸(높이 44px)을 세로로 쌓는다. 표 형식(회색 라벨 칸)은 쓰지 않는다. 파일 고르기는 끌어다 놓는 영역 + 숨긴 입력칸

### 색 토큰

Tailwind 4 `@theme`에 아래 이름으로 등록한다(`src/index.css`).

| 토큰 | 라이트 / 다크 | 쓰는 곳 |
| --- | --- | --- |
| `heading` / `heading-soft` | `#1c1b19` / `#3a3733` (다크 `#efebe4` / `#d9d4cb`) | 페이지·카드 제목 / 섹션 제목, 표 상단선 |
| `primary` / `on-primary` / `primary-hover` | `#23407a` / `#ffffff` / `#1a3163` (다크 `#a9bfee` / `#10141f` / `#c4d3f4`) | 잉크 파랑. 모든 버튼·글자 링크, 선택된 메뉴, 로고, 숙련도 점수 막대 |
| `primary-soft` / `primary-pill` | `#e9edf5` / `#dde3ef` (다크 `#232a3a` / `#2b3448`) | 작은 강조: 아이콘 상자, 선택된 단계·버튼 / 알약형 보조 버튼 |
| `surface` / `surface-raised` / `surface-muted` | `#f8f7f4` / `#ffffff` / `#f2f0eb` (다크 `#161513` / `#1c1b19` / `#262421`) | 사이트 바탕 / 카드·입력칸·드롭다운 / 표 머리·연한 칸·막대 바탕 |
| `section` / `footer` | `#f2f0eb` (다크 `#201e1b`, 푸터 `#121110`) | 큰 안내 상자 / 푸터 배경 (글자는 `heading`) |
| `line` / `line-strong` | `#e8e5df` / `#d3cec5` (다크 `#2e2c28` / `#3d3a35`) | 구분선, 카드 테두리 |
| `input-border` | `#8a857c` (다크 `#7a756c`) | 입력칸·검색칸 테두리 (바탕 대비 3:1 이상) |
| `ink` / `ink-soft` / `ink-muted` | `#1c1b19` / `#57534c` / `#6f6a62` (다크 `#efebe4` / `#aaa398` / `#948e84`) | 본문 / 보조 글자 / 캡션·메타 (모든 바탕에서 4.5:1 이상) |
| `ai` / `ai-soft` / `ai-border` / `ai-chip` | `#6a45c2` / `#f7f4fc` / `#ddd3f3` / `#f1ecfa` (다크 `#c3b2f5` / `#221d2e` / `#3a3150` / `#2a2438`) | AI가 만든 콘텐츠만 |
| `highlight` / `on-highlight` | `#fde68a` / `#3f2d00` (다크 `#6b5310` / `#fff3c4`) | 가져온 대화에서 찾은 키워드·검색어 형광펜(고유 표시)만, 메인 첫 화면 예시 장면 포함 (가린 표시는 `primary-soft`) |
| `strength` / `strength-soft` | `#1f7a46` / `#e8f3ec` (다크 `#7fd6a2` / `#16271d`) | 강점 표시 |
| `weakness` / `weakness-soft` / `weakness-bar` | `#b8432f` / `#fbece8` / `#d9604c` (다크 `#f2a497` / `#33201c` / `#e07a69`) | 보완 필요 표시 / 그 바탕 / 보완 필요 개념의 점수 막대 |
| `chart-pre` / `chart-post` | `#eb6834` / `#23407a` (다크 `#d95926` / `#6f93df`) | 사전·사후평가 비교 그래프 |
| `danger` | `#b91c1c` (다크 `#fca5a5`) | 오류 메시지만 |
| `shade` | `#1c1b19` (다크 `#000000`) | 그림자 색 (`shadow-shade/10`처럼 투명도는 쓰는 곳에서) |
| `overlay` | `#1c1b19` (다크 `#000000`) | 확인 창 뒤를 덮는 막 (`backdrop:bg-overlay/50`) |

- `ai` 색을 쓰는 곳: AI 설명, 판단 근거 요약, AI 추천 학습 순서와 "이 순서인 이유", 챗봇 답변, 대시보드 "왜 여기에 있나요?"
- 다크모드는 같은 중립 회색을 어둡게 옮긴 바탕(`surface` < 카드 `surface-raised` < 연한 칸 `surface-muted`)에 밝은 잉크 파랑(`#a9bfee`)이다. 기본값은 기기 설정을 따르고, 상단바의 화면 모드 버튼 하나(`ThemeToggle`, 누르면 지금 보이는 모드의 반대로 바뀜, `useThemeStore`)로 바꾼다.
- 페이지 본문 폭은 `max-w-page`(1200px), 자주 쓰는 글자 크기는 `text-body`(15px)·`text-caption`(13px)·`text-display`(40px) 토큰을 쓴다.
- 폰트: Pretendard (대체: Noto Sans KR, 시스템 폰트)
