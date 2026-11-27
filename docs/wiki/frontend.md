# 프론트엔드 교훈

## 직접 만든 글자 크기 토큰이 `cn()`에서 지워진다 (2026-10-28, #72)

- 상황: `text-caption`처럼 직접 만든 토큰 클래스를 `cn()`(clsx + tailwind-merge)에 넘겼다.
- 원인: tailwind-merge가 모르는 `text-*`를 글자 색으로 보고, 같은 종류의 다른 클래스로 판단해 지운다(테스트로 확인).
- 해결: `utils/cn.ts`에 직접 만든 글자 크기(`text-body`·`text-caption`·`text-display`)와 페이지 폭(`max-w-page`) 토큰을 알려 준다.
- 다음에는: `@theme`에 `--text-*`·`--container-*` 토큰을 추가하면 `cn.ts`에도 이름을 넣는다. 규칙은 `frontend/CLAUDE.md` "공통 부품 먼저" 참고.

## 화면 모드 전환을 색 섞기로 하면 글자가 잠깐 사라진다 (2026-10-25, #27)

- 상황: 라이트↔다크 전환을 CSS로 색마다 서서히 바꿨다.
- 원인: 글자색과 배경색이 서로 반대로 뒤집히는 전환을 색별로 섞으면 중간에 두 밝기가 같아져 대비가 1.5~2.5까지 떨어진다. 컷편집처럼 보였다.
- 해결: View Transitions로 이전 화면과 새 화면을 겹쳐 서서히 바꾼다. 지원하지 않는 브라우저는 `@property`로 등록한 색 토큰으로 폴백한다.
- 다음에는: 미리보기 창이 숨겨져 있으면 프레임이 안 그려져 전환이 시작되지 않는다(화면 모드가 안 바뀐 것처럼 보임). 눈으로 확인할 때는 창을 보이게 한다. 새 색 토큰은 `styles/theme-transition.css`에도 등록한다.

## 굴절 유리를 넓게 걸면 스크롤이 느려진다 (2026-10-27, #69)

- 상황: 상단바 알약에 굴절(SVG 필터) 유리를 걸었다.
- 원인: SVG 필터가 CPU로 그려져서 넓은 면에 걸면 스크롤이 느려진다.
- 해결: 휘는 가장자리 조각에만 필터를 걸고 가운데는 흐림만 둔다. 스크롤할 때 켜고 끄는 방식은 쓰지 않는다(굴절 지도가 조각 크기와 무관해 끄고 켤 필요가 없다).
- 다음에는: 그래도 무겁다면 `GlassSurface`의 띠 두께(`band`)를 줄이거나 `chromatic`을 0으로 한다. 크롬·엣지만 굴절하고, 다른 브라우저는 흐림만 한다.
- 11/24 이후: 굴절을 통째로 뺐다. 흰 바탕에서는 굴절·흐림·불투명 알약이 사실상 같아 보였고(같은 화면에 CSS만 바꿔 찍어 비교), 굴절이 보이는 건 첫 화면 파란 띠 위를 스크롤하는 순간뿐이었다. 꾸밈 효과는 넣기 전에 "어느 화면에서 보이는가"를 바탕색별로 먼저 찍어 본다.

## `width: auto`는 전환되지 않는다 (2026-10-27, #68)

- 상황: 스크롤하면 상단바가 가운데 알약으로 줄어드는 폭 전환을 만들었다.
- 원인: `width: auto`에서 다른 값으로의 `transition`은 동작하지 않는다.
- 해결: 로고·메뉴 칸·오른쪽 묶음의 너비를 더해 CSS 변수 `--capsule`로 넣는다(`components/layout/useCapsule.ts`).
- 다음에는: 여백 값은 Header의 클래스와 맞춰 둔다. 한쪽만 바꾸면 알약 폭이 어긋난다.
- 11/6 이후: 상단바를 처음부터 본문 폭 알약 하나로 바꿔 이 폭 계산(`--capsule`)은 없어졌다. 아래 "상단바 변신 전환을 알약 하나로 정리" 참고.

## 직접 쓴 CSS는 `components` 레이어에 둬야 Tailwind 클래스가 이긴다 (2026-10-27, #69)

- 상황: `glass.css`의 스타일이 `absolute` 같은 Tailwind 클래스를 덮어썼다.
- 원인: 레이어 밖의 CSS가 레이어 안의 Tailwind 유틸보다 우선한다.
- 해결: 유리 CSS를 `components` 레이어에 두어 Tailwind 유틸 클래스가 이기게 했다.
- 다음에는: 직접 쓰는 공통 CSS는 `@layer components`에 둔다.

## axios 기본 헤더가 JSON이면 FormData 업로드에서 파일이 빠진다 (2026-11-01, #94)

- 상황: 자료 올리기(`uploadMaterial`)를 실제 백엔드에 연결했더니 415 "지원하지 않는 요청 형식이에요."가 왔다.
- 원인: `api/axios.ts`의 기본 헤더가 `Content-Type: application/json`이다. axios 1.x는 이 헤더가 있으면 FormData를 JSON 글자로 바꿔 보낸다(`lib/defaults/index.js`).
- 해결: 업로드 요청에만 `headers: { 'Content-Type': 'multipart/form-data' }`를 준다. 브라우저에서는 axios가 이 값을 지우고 브라우저가 boundary를 붙인 헤더를 만든다. 큰 파일이라 `timeout`도 따로 늘린다.
- 다음에는: 파일을 보내는 API를 새로 만들 때 같은 설정을 쓴다. 목업 모드에서는 요청을 보내지 않아 드러나지 않는다.

## zip 로컬 헤더의 크기 칸이 0xFFFFFFFF면 ZIP64 확장 필드를 읽는다 (2026-11-01)

- 상황: 큰 내보내기 zip을 `Blob.slice`로 읽는 리더를 직접 만들다가, 목차 없이 앞에서부터 읽는 길에서 크기가 전부 4,294,967,295로 나왔다.
- 원인: Claude 내보내기 zip은 4.8MB짜리 파일도 로컬 헤더 크기 칸을 `0xFFFFFFFF`로 두고 실제 크기를 ZIP64 확장 필드(id 1)에 적는다. 로컬 헤더의 확장 필드에는 원본·압축 크기가 둘 다 들어 있다(목차와 달리 칸별로 건너뛰지 않는다).
- 해결: 헤더 칸이 `0xFFFFFFFF`이면 확장 필드에서 원본 → 압축 크기 순으로 읽는다(`zipReader.ts`).
- 다음에는: zip 형식을 직접 읽을 때는 작은 파일도 ZIP64일 수 있다고 보고, 가짜 zip 테스트에 ZIP64 확장 헤더를 꼭 넣는다. 크기가 데이터 뒤에 적히는 방식(data descriptor)은 아직 지원하지 않는다.

## 코드 블록 안의 글자는 마크다운 플러그인으로 못 칠한다: CSS Custom Highlight로 짚는다 (2026-11-02)

- 상황: 가린 곳 목록의 "보기"로 본문의 가린 표시 하나를 진하게 칠하려는데, `[IP]`가 코드 블록 안에 있으면 칠해지지 않았다.
- 원인: `remarkMarks`는 코드 노드(`code`·`inlineCode`)를 건너뛰어서 코드 안의 가린 표시는 그냥 글자다. 네트워크 공부 대화는 IP 예시가 코드 블록에 자주 나온다.
- 해결: 그려진 DOM에서 가린 표시의 n번째 글자 자리를 `Range`로 찾아(`findMaskLabelRange`) `CSS.highlights`로 칠한다(요소를 바꾸지 않아서 코드 블록 안도 된다). n은 `collectMaskedSpots`의 `labelIndex`(사용자가 직접 쓴 `[IP]`도 센다). 지원하지 않는 브라우저는 메시지 테두리만 보인다.
- 다음에는: `::highlight(...)`는 빌드 때 "not recognized as a valid pseudo-element" 경고가 한 번 나오지만 결과 CSS에는 그대로 들어간다(`dist`에서 확인).

## 상단바 변신 전환을 알약 하나로 정리 (2026-11-06)

- 상황: 맨 위 흰 상단바 → 스크롤 시 알약 전환을 다듬는 동안 끊김·메뉴 순간 이동·로고 줄바꿈·알약 뒤 네모가 차례로 생겼다.
- 원인: ① 전환 중에 목표 폭을 매 프레임 다시 재면 `transition`이 계속 새로 시작된다 ② `flex: 1`↔`none`처럼 축약값을 바꾸면 전환되지 않는다 ③ `basis-0`에 `min-w-max`가 없으면 "ai-lms"가 하이픈에서 끊긴다 ④ `GlassSurface`의 `border-radius: inherit`는 바로 바깥 요소에서 물려받아, `display: contents` 상자로 감싸면 네모가 된다.
- 해결: 넓은 화면은 처음부터 본문 폭 알약 하나(스크롤 시 높이만 64→56px, 검색칸 접힘)로 바꾸고, 검색칸 자리는 펼친 폭으로 고정, 유리 바탕은 알약 바로 안에 `className`으로 숨김 처리.
- 다음에는: 모양이 바뀌는 전환은 목표값을 상태에서 바로 정하고, 한 부분을 다시 짤 때는 그동안 맞춘 동작 목록을 하나씩 다시 잰다.

## 목업 화면 상단바만 로그인 상태가 되고 화면은 로그인으로 보낸다 (2026-11-06)

- 상황: 같은 PC에서 목업 개발 서버(5173)와 실제 API 개발 서버(5174)를 함께 띄우고 5174에서 로그인한 뒤 5173을 열었다.
- 원인: 쿠키는 포트를 가리지 않아 두 서버가 Refresh Token 쿠키를 같이 쓴다. 목업 모드도 시작할 때 `restoreSession()`이 실제 백엔드(프록시)로 갱신을 요청해 성공했고, 상단바는 로그인 상태가 됐지만 목업 가드(`RequireAuth`)는 체험 모드만 봐서 로그인 화면으로 보냈다.
- 해결: `USE_MOCK`이면 `restoreSession()`이 세션을 되살리지 않는다(`api/auth.api.ts`).
- 다음에는: 목업·실제 모드를 동시에 띄울 때 localhost 쿠키·localStorage를 같이 쓴다는 점을 먼저 의심한다(포트가 달라도 쿠키는 공유, localStorage는 포트별).

## 오류 화면의 "다시 시도"를 눌러도 같은 오류 화면이 남는다 (2026-11-20)

- 상황: 퀴즈 목록 요청을 일부러 실패시킨 뒤 서버를 되살리고 "다시 시도"를 두 번 눌러도 "내용을 불러오지 못했어요"가 그대로였다. 전체 새로고침만 풀렸다.
- 원인: `useSuspenseQuery`의 오류는 `QueryErrorResetBoundary`의 `reset`을 불러야 지워진다. `AsyncBoundary`의 `ErrorBoundary`만 다시 그리면 React Query가 실패한 조회를 다시 부르지 않고 같은 오류를 또 던진다.
- 해결: `AsyncBoundary`를 `QueryErrorResetBoundary`로 감싸 `onReset={reset}`을 넘기고, 주소가 바뀌면 오류 화면을 지우도록 `resetKeys`에 경로를 넣었다.
- 다음에는: 오류 복구는 이미 불러온 데이터가 캐시에 있으면 재현되지 않는다(1분 `staleTime`). 처음 여는 화면에서 요청을 막고 확인한다.

## 배포 직후 열려 있던 탭에서 화면을 옮기면 영어 기본 오류 화면이 뜬다 (2026-11-20)

- 상황: 빌드본에서 퀴즈 풀기 화면 코드 파일을 지우고 목록에서 "풀기"를 누르자 React Router 기본 화면("Unexpected Application Error!")이 나왔다. 새 배포 뒤 옛 탭에서 같은 일이 생긴다.
- 원인: 페이지는 처음 방문할 때 코드를 불러오는데(`route.lazy`), 배포가 바뀌면 옛 파일 이름이 없어진다. 라우터에 `errorElement`가 없었다.
- 해결: 루트에 `RouteErrorPage`를 두고, 화면 코드 불러오기 실패(`isChunkLoadError`)면 30초 안에 한 번만 자동 새로고침한다(`shouldAutoReload`, sessionStorage에 시각 기록). 두 번째도 실패하면 "새 버전이 나왔어요" 화면을 보여 준다.
- 다음에는: 개발 서버에서는 재현되지 않는다. `vite build` 결과를 `vite preview`로 띄우고 `assets`의 페이지 파일을 지워서 확인한다.

## JS 정규식을 러스트로 옮기면 결과가 달라지거나 느려진다 (2026-11-21, #186)

- 상황: 대화 가리기 규칙(`mask.ts`)을 러스트 WASM으로 옮겼다.
- 원인: 러스트 `regex`의 `\d`·`\b`·`\s`는 유니코드라 한글 옆에서 JS와 다르고, 앞뒤 확인(lookaround)이 없다. 되돌아가며 찾는 `fancy_regex`는 결과는 같았지만 40배 넘게 느렸다(글 344만 자 2.3초).
- 해결: `\d`·`\b`는 ASCII로 직접 쓰고, 앞 글자 확인은 찾은 뒤 코드로(안 맞으면 한 글자 뒤부터 다시), 뒤 글자 확인은 그 글자까지 찾고 뺐다. 예전 JS와 무작위 글 3만 개 결과를 비교해 같음을 확인했다. 그래도 가리기는 V8 정규식(JIT)보다 빠르지 않았다(zip 풀기는 2.3배 빨라짐).
- 다음에는: JS를 WASM으로 옮기기 전에 같은 입력으로 시간을 먼저 잰다. 위치는 UTF-16 단위로 돌려줘야 JS 문자열과 맞는다.

## thinking-orbs 구슬이 밝은 화면에서 안 보이고 32px에서 비어 보인다 (2026-11-23, #200)

- 상황: AI 대기 상자(`AiWaiting`)에 `thinking-orbs`를 넣었다.
- 원인: 기본 흑백은 밝은 화면의 연한 파랑 바탕에 묻힌다. 32는 64·20 사이를 계산한 크기라 점이 적다(`connecting` 약 40개 → 15개). 색(`color`)은 hex·rgb만 받아 CSS 변수를 못 넘기고, 화면 모드 폴백 전환 중에 읽으면 바뀌기 전 색이 들어간다.
- 해결: `--color-primary`를 읽어 넘기고 400ms 뒤 한 번 더 읽는다(`AiOrb`). `dots`·`dotSize`로 점을 채웠다. `import()`로 나눠 불러와 공통 묶음에 넣지 않았다.
- 다음에는: 구슬 크기나 움직임을 바꾸면 밝은·어두운 화면에서 1:1 크기로 다시 본다(축소 스크린샷에서는 실제보다 흐려 보인다).

## border-beam 빛이 밝은 화면에서 거의 안 보이고 화면 모드를 안 따라간다 (2026-11-24, #201)

- 상황: 분석 결과 카드에 `border-beam` 테두리 빛을 넣었다(`ResultBeam`).
- 원인: 밝은 화면 설정은 진하기가 낮고(선 0.12·안쪽 0.26) 선이 검정이라 회색 줄로 보인다. `brightness`·`strength`(최대 1)로는 부족했다. `theme="auto"`는 기기 설정만 봐서 상단바로 고른 모드를 모른다.
- 해결: 라이브러리가 열어 둔 층별 배율 CSS 변수(`--beam-inner-opacity`·`--beam-bloom-opacity`·`--beam-stroke-opacity`)를 `style`로 넘겼다(페이드 효과는 그대로). `<html>`의 `dark` 클래스를 읽어 `theme`을 직접 넘긴다.
- 다음에는: 라이브러리 진하기가 약하면 생성된 CSS에서 `var(--beam-…, 1)` 같은 배율 변수부터 찾는다.

## 좌우로 나눈 표·상세에서 표 옆이 크게 빈다 (2026-11-24, #202)

- 상황: 학습 대시보드에서 개념 표(왼쪽)와 고른 개념의 상세(오른쪽)를 두 칸 그리드로 나란히 뒀다.
- 원인: 상세가 숙련도·근거·변화·모델별 판단까지 약 2,100px로 길었고, 그리드 칸은 같은 줄의 가장 긴 칸에 맞춰 늘어나서(카드가 `h-full`) 표 카드 아래가 1,700px쯤 빈 흰 칸이 됐다.
- 해결: 표와 상세를 나누지 않고, 개념 한 줄을 누르면 그 자리에서 펼치는 목록으로 바꿨다(`ConceptList`). 영역은 모두 한 줄 전체로 쌓는다.
- 다음에는: 길이가 크게 다른 두 영역을 한 줄에 두지 않는다. 꼭 나란히 둬야 하면 짧은 쪽에 `self-start`(필요하면 `sticky`)를 준다.

## `disabled={isPending}`만으로는 저장 버튼 더블클릭을 못 막는다 (2026-11-24, #210)

- 상황: 로컬 점검에서 "자료 올리기"를 더블클릭하자 같은 16MB 파일이 두 번 저장됐고, 공지 올리기도 두 건, "퀴즈 만들기" 10번 연타는 POST 10번이 갔다.
- 원인: `isPending`은 `mutate` 뒤 화면을 다시 그려야 버튼에 반영된다. 그 전에 들어온 두 번째 클릭은 아직 눌리는 버튼이라 또 보낸다. 핸들러 안의 `if (mutation.isPending) return`도 같은 렌더의 옛 값을 본다.
- 해결: 서버 데이터를 바꾸는 `useMutation`을 모두 `useSingleFlightMutation`(`hooks/`)으로 바꿨다. `mutate` 직전에 동기 잠금(`utils/submitLock.ts`)을 걸고 `onSettled`에서 푼다. 자료 올리기는 서버도 요청 키(`requestKey`)로 같은 올리기를 한 번만 저장한다.
- 다음에는: 저장 버튼에 새 mutation을 만들 때 `useMutation` 대신 `useSingleFlightMutation`을 쓴다. 파일·비용이 큰 요청은 서버 요청 키도 함께 둔다.

## `src` 안 테스트에서 `node:` 모듈을 가져오면 build가 깨진다 (2026-11-25, #240)

- 상황: `zipReader.test.ts`의 CRC 계산을 `node:zlib`의 `crc32`로 바꿨다. `npm test`는 통과했다.
- 원인: `tsconfig.app.json`이 `src` 전체(테스트 포함)를 검사하는데 `types`가 `vite/client`뿐이라 `tsc -b`가 `node:zlib`를 못 찾는다(TS2591). Node 타입을 쓰는 테스트 초기화(`src/test`)는 그래서 검사에서 빼 두었다.
- 해결: Node 타입을 앱 전체에 열지 않고, 테스트 안에 CRC-32를 직접 적었다.
- 다음에는: 테스트에서 Node 전용 모듈이 필요하면 `npm test`만 보지 말고 `npm run build`까지 돌려 본다.

## 화면에서 고른 대화가 분석 때 "키워드가 없다"로 빠질 수 있다: 프론트·백엔드 키워드 규칙을 같이 바꾼다 (2026-11-25, fix/keyword-word-boundary)

- 상황: 대화 고르기(`filter.ts`)와 서버 분석 입력(`AnalysisInputBuilder`)이 둘 다 부분 문자열로 키워드를 찾아 "IP"가 "ship"에 걸렸다. 자료 쪽 `SourceText`만 영문 용어를 낱말 경계로 찾고 있었다.
- 원인: 같은 판단을 세 곳에 따로 썼다. 한쪽만 고치면 화면에서 고른 대화가 서버에서 빠지거나, 반대로 화면에서 안 고른 메시지가 서버에서 골라진다.
- 해결: 영문 키워드만 낱말 경계(앞뒤가 ASCII 영문·숫자가 아님)로 찾는 규칙을 백엔드 `global/common/TermMatch`로 모으고 프론트 `filter.ts`·대화 보기 형광펜(`marks.ts`)도 같은 규칙으로 바꿨다. 양쪽 테스트에 같은 표(`TermMatchTest`·`filter.test.ts`)를 둔다. JS 정규식은 앞 글자 확인 대신 `(?:^|[^a-z0-9])`로 쓰고, 형광펜은 여러 단어를 이어 칠해서 앞 글자를 코드로 본다.
- 다음에는: 키워드로 무언가를 찾는 곳을 새로 만들면 `TermMatch`·`filter.ts`를 쓰고, 규칙을 바꾸면 두 표를 같이 고친다.

## ESLint 규칙에 파일별 예외를 걸면 그 파일에서 같은 규칙의 다른 항목도 사라진다 (2026-11-25, chore/frontend-lint-rules)

- 상황: `no-restricted-syntax`·`no-restricted-imports` 하나에 프로젝트 규칙 여러 개를 넣고, `useDemoStore.ts`·`useSingleFlightMutation.ts`에서만 한 항목을 빼려 했다.
- 원인: flat config에서 `files` 객체로 같은 규칙을 다시 걸면 옵션이 합쳐지지 않고 통째로 바뀐다(`npx eslint --print-config <파일>`로 확인). 뺄 항목만 생각하고 예외를 쓰면 그 파일에서 나머지 금지도 같이 풀린다. 또 `react-dom/no-dangerously-set-innerhtml`은 권장 설정에서 경고라 `npm run lint`가 실패하지 않아 CI에서 막히지 않았다.
- 해결: 항목을 `eslint.config.js` 위쪽 상수로 나눠 두고, 예외 객체에는 남길 항목만 다시 넘긴다. 금지는 `error`로 건다.
- 다음에는: 예외를 더하면 그 파일에 위반 코드를 잠깐 넣어 다른 항목이 아직 잡히는지 `npm run lint`로 본다(설정이 바뀌면 캐시가 있어도 다시 검사한다).

## Tailwind 4의 `outline-none`은 고대비 모드에서 포커스를 지운다: `outline-hidden`을 쓴다 (2026-11-27, fix/frontend-regressions)

- 상황: 입력칸(`fieldClass`·상단바 검색칸·과목 키워드 입력칸)이 포커스 표시를 테두리 색으로 대신하려고 `outline-none`을 쓰고 있었다.
- 원인: Tailwind 4에서 `outline-none`은 `outline-style: none`이다(3의 투명 outline이 아님). 고대비(강제 색) 모드는 배경·테두리 색을 덮어써서 테두리 색 변화가 사라지고, outline도 없으니 포커스가 전혀 안 보인다.
- 해결: `outline-hidden`으로 바꿨다. 평소에는 outline이 없고 `@media (forced-colors: active)`에서만 `outline: 2px solid transparent`(강제 색 모드는 이 투명색을 보이는 색으로 바꾼다)가 붙는다. 빌드한 CSS에서 이 규칙을 확인했다.
- 다음에는: 포커스 테두리를 숨기는 곳은 `outline-hidden`만 쓴다(`frontend/CLAUDE.md` 접근성 줄). `tabIndex=-1`처럼 키보드로 못 들어가는 요소만 예외다.

## 체험 모드에서는 마이페이지가 안 떠 확인 창 여러 개를 화면으로 볼 수 없다 (2026-11-27, fix/frontend-regressions)

- 상황: 마이페이지에 함께 있는 확인 창 3개의 `aria-labelledby`가 각자 제목을 가리키는지 목업 미리보기에서 보려 했다.
- 원인: 마이페이지는 체험에서 "보여 드리지 않아요" 안내만 띄우고, 목업 모드는 로그인이 없어 실제 화면을 열 수 없다.
- 해결: 미리보기 페이지에서 `javascript_tool`로 Vite 모듈(`/src/components/common/ConfirmDialog.tsx`, `main.tsx`가 가져오는 `/node_modules/.vite/deps/react.js?v=…`·`react-dom_client.js?v=…` 주소 그대로)을 `import()`해 빈 `div`에 창 3개를 그려 id를 확인하고 바로 지웠다. 버전 꼬리(`?v=`)가 다르면 React가 둘이 되거나 모듈을 못 읽는다.
- 다음에는: 체험에서 안 열리는 화면의 컴포넌트는 `fetch('/src/main.tsx')`로 deps 주소를 먼저 확인한 뒤 같은 방식으로 따로 그려 본다.

## `src/test/` 초기화 파일의 `vi.mock('@/…')`이 아무 일도 하지 않는다 (2026-11-27, chore/test-hardening)

- 상황: 목업 지연(`mocks/delay.ts`)을 모든 테스트에서 건너뛰려고 `setupFiles`에 `vi.mock('@/mocks/delay', …)`를 넣었는데, 오류 없이 테스트가 여전히 6초 걸렸다.
- 원인: `@/` 별칭은 Vite `resolve.tsconfigPaths`가 파일을 포함한 tsconfig로 푼다. `tsconfig.app.json`이 `src/test`를 빼 두어 그 안에서는 `@/`가 풀리지 않고, `vi.mock`은 맞는 모듈이 없어도 조용히 넘어간다.
- 해결: 초기화 파일에서는 상대 경로(`vi.mock('../mocks/delay', …)`)로 썼다.
- 다음에는: `src/test/` 안에서는 `@/`를 쓰지 않는다. `vi.mock`을 넣은 뒤에는 효과(시간·호출 수)가 실제로 바뀌었는지 한 번 본다.

## 대시보드가 불러오는 동안 짧은 회색 틀을 보여 주면 다 그린 뒤 아래 구역이 밀린다 (2026-11-27, fix/dashboard-layout-shift)

- 상황: Lighthouse 데스크톱에서 `/?demo=1`(→ `/analysis`)의 CLS가 0.676이었다. 휴대폰은 0.
- 원인: 공통 틀 `LoadingFallback`은 약 360px라 ① 과목을 불러오는 동안 바닥글이 첫 화면에 보였다가 밀려나고(0.49) ② 분석 결과보다 먼저 그려진 사전·사후평가 구역이 결과가 들어오며 밀려났다(0.185). 휴대폰은 화면이 좁아 처음부터 둘 다 첫 화면 밖이었다. Motion의 투명도·`y` 이동(transform)은 CLS에 들어가지 않았다.
- 해결: `AsyncBoundary`에 `fallback`을 받게 하고, 대시보드는 결과 자리를 화면 높이(`min-h-screen`)만큼 잡는 `DashboardSkeleton`을 넘겼다. 같은 측정에서 0.676 → 0.
- 다음에는: 내장 브라우저 창이 숨겨져 있으면 `layout-shift` 기록이 0으로 나온다(화면을 안 그린다). 창이 숨겨져 있으면 헤드리스 Chrome을 CDP로 띄워 같은 `PerformanceObserver`로 잰다(Lighthouse와 같은 값이 나왔다). 밀린 요소는 `sources[].node`, 위치 변화는 `previousRect`·`currentRect`로 본다.

## 닫히며 사라지는 햄버거 메뉴의 링크로 옮기면 포커스가 본문으로 가지 않는다 (2026-11-27, fix/a11y-navigation)

- 상황: 화면을 옮긴 뒤 "포커스를 잃었을 때만" 본문(`main`)으로 옮기는 `RouteAnnouncer`를 넣었는데, 휴대폰 폭 햄버거 메뉴로 옮기면 포커스가 body로 떨어졌다.
- 원인: `AnimatePresence`가 닫히는 애니메이션 동안 메뉴를 DOM에 남겨 둔다. 주소가 바뀐 직후에는 포커스가 아직 메뉴 링크에 있어 "잃지 않음"으로 보고, 애니메이션이 끝나 메뉴가 사라질 때 잃는다. 백그라운드 탭에서는 애니메이션이 멈춰 메뉴가 계속 남아 있어 미리보기로는 잘 안 보였다.
- 해결: `MobileMenu`의 링크를 누를 때 닫기와 함께 `focusMainContent()`를 불러 미리 본문으로 옮겼다.
- 다음에는: 퇴장 애니메이션이 있는 패널 안에서 이동하거나 패널을 닫는 동작은 포커스를 직접 옮긴다. 미리보기에서 확인할 때는 `document.activeElement.isConnected`와 패널이 실제로 사라졌는지도 본다.

## 컴포넌트와 도우미 파일 이름이 대소문자만 다르면 macOS에서 import가 엉뚱한 파일로 풀린다 (2026-11-27, fix/a11y-live-status)

- 상황: 컴포넌트 `AiWaitingStatus.tsx` 옆에 로직 파일을 `aiWaitingStatus.ts`로 두자 `tsc -b`가 TS1261("only in casing")과 "default 내보내기가 없다"로 실패했다.
- 원인: macOS 기본 파일 시스템은 대소문자를 구분하지 않아 `'./AiWaitingStatus'`가 확장자 순서대로 `.ts`(도우미)로 풀렸다.
- 해결: 도우미를 `aiWaitingMessage.ts`처럼 다른 낱말로 지었다.
- 다음에는: 컴포넌트 전용 로직 파일(`react-refresh/only-export-components` 때문에 나누는 것)은 컴포넌트 이름의 첫 글자만 소문자로 바꾸지 말고 다른 이름을 붙인다.

## 포커스 직후 읽은 계산 스타일이 옛 값이라 포커스 표시가 안 붙은 줄 안다 (2026-11-27, fix/a11y-focus-contrast)

- 상황: 입력칸 포커스에 `focus-within:ring-1`을 더하고 미리보기에서 Tab 직후 `getComputedStyle`로 테두리·`box-shadow`를 읽었더니 회색 테두리·빈 그림자가 나와 클래스가 안 먹는 줄 알았다.
- 원인: `index.css`가 `input`·`select` 등에 `border-color`·`box-shadow` 전환을 걸어 두어, 포커스 바로 뒤에는 전환 시작 값이 읽힌다. 그 순간 찍은 스크린샷도 옛 모양이다.
- 해결: 수백 ms 기다린 뒤 다시 읽어 파랑 테두리와 1px ring이 붙은 것을 확인했다.
- 다음에는: 포커스·호버 모양을 스크립트나 스크린샷으로 확인할 때는 전환 시간보다 길게 기다린 뒤 읽는다.
