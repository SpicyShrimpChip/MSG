# 프론트가 백엔드에 바라는 것

프론트 화면이 기대하는 API 계약을 모은 문서다. 확정은 백엔드 API 문서(Swagger)에서 하고, 바뀌면 이 문서·프론트 타입·목업을 같은 PR에서 고친다.

근거 기반 학습(인지 수준·자료 근거 퀴즈·요약본 API·대화 분석 학습 행동·개념 검증 상태·커리큘럼 "왜 먼저")의 API·응답 필드는 `docs/evidence-based-learning.md` 8절에 있다. 모두 더하기만 했다.

## 요약 (먼저 읽을 곳)

- **마이그레이션:** 지금 목록과 다음 번호는 `backend/CLAUDE.md` "DB 마이그레이션"과 `docs/backend-guide.md` 3절이 기준이다. 이 문서에는 마이그레이션 번호를 적지 않는다.
- **프론트가 연결해 둔 것:** 공지(목록·상세·관리자 작성), 내 과목, 학습자료, 인증·카카오 로그인·마이페이지, AI 대화 가져오기 저장, 학습 분석(모델 비교·숙련도 `mastery`·`history`·`quizNeeded`·"이 판단 맞나요" 피드백 `PUT·DELETE`), 퀴즈(풀기·채점·AI 만들기), 사전·사후평가, 통합 검색, 맞춤 커리큘럼 `flow`, 개념 학습 기록.
- **프론트가 아직 안 쓰는 것:** 가져온 대화 목록·삭제 화면(시안 먼저), 챗봇.
- **백엔드와 정할 것:** 5번.
- **비용 절감:** Claude 프롬프트 캐싱(`docs/backend-guide.md` 4절). 고정 지시문을 앞에 둔다.

## 1. 개발 환경 연결

- 프론트는 모든 요청을 **`/api/...`** 로 보낸다. 컨트롤러 경로도 `/api`로 시작한다. 예: `@RequestMapping("/api/courses")`
- 스프링은 **8080** 포트로 띄운다. 프론트 개발 서버(5173)가 `/api` 요청을 8080으로 넘겨 준다(Vite 프록시).
  - 브라우저 입장에서는 같은 출처라서 **개발 중에는 CORS 설정 없이도 된다.**
  - 배포도 Vercel이 `/api`를 백엔드로 넘기는 방식(rewrite)이라 같은 출처다(`docs/deploy.md`). 프론트를 다른 도메인에서 직접 호출하게 되면 그때 CORS를 연다.
- 프론트는 `.env.development`의 `VITE_USE_MOCK=true`로 가짜 데이터를 쓴다. 백엔드를 띄워 놓고 실제로 붙여 보려면 `frontend/.env.development.local`(커밋 안 됨)에 `VITE_USE_MOCK=false`를 넣는다.

## 2. 공통 응답 형식

```json
{ "success": true, "message": "", "data": {} }
```

- 성공·실패 모두 이 모양으로 보낸다. 실패 때는 `success: false`와 적절한 HTTP 상태 코드(400, 401, 403, 404, 409, 422, 429, 500 등)를 함께 쓴다. 실패에도 화면이 써야 할 값이 있으면 `data`에 담는다(예: 대화 가져오기 422의 `data.rejected`, `BusinessException`의 `data`).
- **`message`는 사용자에게 그대로 보여 준다.** 프론트 오류 화면이 이 문구를 띄운다(`frontend/src/api/request.ts`).
  - 좋은 예: "이 학기에 같은 이름의 과목이 이미 있어요." / "파일 형식을 확인해 주세요."
  - 나쁜 예: 영어 예외 메시지, 스택트레이스, SQL 오류
- 응답이 없으면(서버 꺼짐·네트워크) 프론트가 알아서 "서버에 연결하지 못했어요"를 띄운다.

전역 예외 처리(`@RestControllerAdvice`)의 상태·문구. 예외 메시지·스택트레이스는 응답에 넣지 않는다(서버 로그에만).

| 상황                  | 상태 | `message` 예시                                                  |
| --------------------- | ---- | --------------------------------------------------------------- |
| 직접 던진 업무 오류   | 코드 | 던질 때 정한 사용자용 문구                                      |
| 입력 검증 실패        | 400  | DTO 검증 문구 중 첫 번째, 없으면 "입력한 내용을 확인해 주세요." |
| 요청 본문을 읽지 못함 | 400  | "요청 형식이 올바르지 않아요."                                  |
| 없는 주소             | 404  | "요청한 주소를 찾을 수 없어요."                                 |
| DB UNIQUE 제약 위반   | 409  | 값은 로그에 남기지 않는다                                       |
| 예상하지 못한 오류    | 500  | "서버에서 문제가 생겼어요. 잠시 뒤 다시 시도해 주세요."         |

## 3. API 목록

### 공지사항 — 구현됨 (확정: Swagger `공지사항`, `공지사항 관리 (관리자)`)

| 메서드 | 경로                      | 권한   | 응답 `data`                                     |
| ------ | ------------------------- | ------ | ----------------------------------------------- |
| GET    | `/api/notices`            | 공개   | `Notice[]` (본문 없음)                          |
| GET    | `/api/notices/{id}`       | 공개   | `NoticeDetail` (없으면 404)                     |
| POST   | `/api/admin/notices`      | 관리자 | `NoticeDetail` (201), 본문 `{ title, content }` |
| PUT    | `/api/admin/notices/{id}` | 관리자 | `NoticeDetail`, 본문 `{ title, content }`       |
| DELETE | `/api/admin/notices/{id}` | 관리자 | 없음 (완전히 삭제)                              |

```ts
Notice = { id: number, title: string, createdAt: string }; // createdAt: "2026-10-24"
NoticeDetail = {
  id: number,
  title: string,
  content: string,
  createdAt: string,
  updatedAt: string | null,
}; // updatedAt: 수정한 적이 없으면 null
```

- 목록은 최신 공지가 위로(작성일 내림차순, 같으면 id 큰 순). `createdAt`은 `"2026-10-24"` 모양의 날짜 문자열이다. 로컬 샘플 3건은 `db/seed/R__sample_notices.sql`(프론트 목업과 같다).
- 제목 100자·본문 5000자까지, 둘 다 필수이고 앞뒤 공백은 서버가 지운다. 어기면 400 + 안내 문구.
- **본문은 글자로만 다룬다.** 서버는 HTML을 해석하지 않고 그대로 저장하며, 프론트도 줄바꿈만 살려 보여 준다(`dangerouslySetInnerHTML` 금지).
- 작성자는 요청 본문이 아니라 토큰의 사용자로 정해 `author_id`에 저장하고, 응답에는 내보내지 않는다. 작성한 관리자가 탈퇴해도 공지는 남는다.
- 일반 사용자는 403, 로그인하지 않으면 401.

### 내 과목 — 구현됨 (확정: Swagger `내 과목`)

화면: `/courses` (학습자료 > 내 과목). 등록한 키워드는 AI 대화 가져오기에서 공부한 대화를 고를 때, 자료 올리기에서 과목을 고를 때 쓴다.

| 메서드 | 경로                | 요청 본문     | 응답 `data`                                            |
| ------ | ------------------- | ------------- | ------------------------------------------------------ |
| GET    | `/api/courses`      | -             | `Course[]` (로그인한 사용자 것만, 등록 순)             |
| POST   | `/api/courses`      | `CourseInput` | `Course` (201)                                         |
| PATCH  | `/api/courses/{id}` | `CourseInput` | `Course` (보낸 값으로 모두 바꿈)                       |
| DELETE | `/api/courses/{id}` | -             | 없음. **그 과목의 학습자료와 원본 파일도 함께 지운다** |

```ts
Course      = { id: number, name: string, semester: string, keywords: string[] }
CourseInput = { name: string, semester: string, keywords: string[] } // id 없음
semester    = "2026-1" | "2026-summer" | "2026-2" | "2026-winter"
```

서버 검사 (프론트 `CourseForm`·`utils/keywords.ts`와 같은 규칙):

- 앞뒤 공백을 지우고 안쪽 공백은 한 칸으로 줄인 뒤 검사한다. 이름 1~40자, 키워드 최대 20개·각 20자, 대소문자만 다른 키워드는 하나로 친다.
- 같은 사용자·같은 학기·같은 이름(대소문자 무시) 과목은 409 "이 학기에 같은 이름의 과목이 이미 있어요."
- 다른 사용자의 과목은 조회되지 않고, 수정·삭제는 404(존재 여부를 알리지 않음).

### 자료 — 구현됨 (확정: Swagger `학습자료`)

화면: `/materials/upload`(올리기), `/materials`(목록, 과목별로 묶음), `/materials/{id}`(상세: 요약 / 핵심 개념 / 퀴즈 탭). 올린 본인만 볼 수 있다.

| 메서드 | 경로                  | 요청 본문                                                                    | 응답 `data`                                             |
| ------ | --------------------- | ---------------------------------------------------------------------------- | ------------------------------------------------------- |
| POST   | `/api/materials`      | `multipart/form-data`: `courseId`(선택), `title`, `file`, `requestKey`(선택) | `Material` (201, 같은 `requestKey`면 200으로 같은 자료) |
| GET    | `/api/materials`      | -                                                                            | `MaterialSummary[]` (최신순)                            |
| GET    | `/api/materials/{id}` | -                                                                            | `MaterialDetail`                                        |
| DELETE | `/api/materials/{id}` | -                                                                            | 없음 (자료 정보와 원본 파일을 함께 지운다)              |

- `requestKey`: 화면이 올리기마다 만든 UUID(64자까지, 넘으면 400 "요청 키가 너무 길어요."). 같은 사용자가 같은 키로 다시 보내면(더블클릭·연타·응답을 못 받은 재시도) 파일을 새로 저장하지 않고 이미 올린 자료를 200으로 돌려준다. 동시에 온 같은 키 요청은 서버가 사용자 행을 잠가 차례로 처리한다. 화면은 응답을 못 받은 실패(시간 초과·연결 끊김)일 때만 같은 키로 다시 보내고, 다른 실패나 파일을 바꾸면 새 키를 만든다. 키 없이 보내면 매번 새로 저장한다.

```ts
Material = { id: number, courseId: number | null, title: string }
MaterialSummary = {
  id: number, courseId: number | null,   // 과목 없이 올린 자료는 null
  title: string,
  fileName: string,                       // 올린 파일 이름 (경로 부분을 뗀 값)
  createdAt: string,                      // ISO, 예: "2026-11-01T12:28:20.081677"
  status: "uploaded" | "processing" | "ready" | "failed",
  quizCount: number                       // 이 자료에 연결된 내 퀴즈 수
}
MaterialDetail = MaterialSummary & {
  aiSummary: string | null,               // 늘 null. 요약은 GET·POST /api/materials/{id}/summary (evidence-based-learning.md 8절)
  concepts: {                             // 분석 전이면 []
    conceptId: string, name: string, aiDescription: string | null,
    evidenceLocation: string | null,      // 쪽·슬라이드 (모르면 null)
    evidenceQuote: string,                // 자료 원문 근거 문장
    courseKeyword: boolean,               // 과목 키워드와 같은 개념인지
    studyNote: ConceptStudyNote | null    // 형식은 5-9-1 `study-notes` 절 참고
  }[]
}
```

- **상태:** `uploaded` = 파일 저장 완료·AI 분석 전, `processing` = 글 뽑기·AI 분석을 실제로 하는 중, `ready` = 완료, `failed` = 처리 실패. 프론트는 `uploaded`에 "AI 분석 전"을 보여 주고 "요약 만드는 중"은 `processing`에만 쓴다.
- **형식·크기:** PDF·PPTX·TXT·MD, 파일 하나 20MB. 서버는 확장자와 함께 **파일 내용**을 확인한다(PDF 머리글·끝 표시, PPTX는 ZIP 안의 PowerPoint 필수 항목 — 다른 ZIP·Word·매크로 포함 PPTM 거절, TXT·MD는 UTF-8 글자 파일). 브라우저가 보낸 `Content-Type`은 믿지 않는다. HWP·HWPX는 2단계 후보.
- **오류:** 형식 400 "PDF, PPTX, TXT, Markdown 파일만 올릴 수 있어요." / 내용이 형식과 다름 400 / 빈 파일 400 / 20MB 초과 413 / 파일 칸 없음 400 "올릴 파일을 골라 주세요." / 남의 과목·없는 과목 404 / multipart가 아님 415 / 저장 실패 500 "파일을 저장하지 못했어요. 잠시 뒤 다시 시도해 주세요." / 내 자료의 크기 합계(기본 200MB)·개수(기본 100개) 상한 초과 409 "저장 공간이 모자라요. … 쓰지 않는 자료를 지운 뒤 다시 올려 주세요."(`app.material`, 화면은 서버 메시지를 그대로 보여 준다)
- **프론트 요청 설정:** axios 기본 헤더(`application/json`)가 남아 있으면 axios가 FormData를 JSON 글자로 바꿔 파일이 빠진다(415). `uploadMaterial`은 `Content-Type: multipart/form-data`(브라우저가 boundary를 붙여 다시 만든다)와 업로드 전용 timeout 120초를 쓴다.
- **삭제 범위:** 자료를 지우면 자료 정보와 원본 파일, 그 자료의 개념·요약·퀴즈·근거(`material` 외래 키 ON DELETE CASCADE)를 지우고, 점수는 남은 근거로 다시 계산한다. **과목을 지우면 그 과목의 자료도 모두 같은 방식으로 지운다.** 과목에 연결하지 않은 자료는 남는다.
- 원본은 자료나 그 과목을 지울 때까지 보관한다. 장기 보관 기간·자동 삭제는 미결(5번).
- 목업(`mocks/materials.ts`)은 시연 과목 샘플 자료 3개(`ready`)로 시작하고, 올린 자료는 `uploaded`로 남는다. 목업에서도 과목을 지우면 그 과목의 자료가 함께 지워진다.

### 인증·마이페이지 — 구현됨 (확정: Swagger `인증`, `회원`)

화면: `/login`, `/signup`, 마이페이지(내 정보, AI 대화 분석·AI 퀴즈 처리 동의 철회·다시 동의, 학습 목적·관심 직무, 탈퇴). 방식은 JWT access token(메모리) + HttpOnly 쿠키 refresh token. 자세한 흐름은 `docs/backend-design.md` 5번. 카카오 로그인은 5-11절.

| 메서드 | 경로                     | 요청 본문                                                                       | 응답 `data`                                          |
| ------ | ------------------------ | ------------------------------------------------------------------------------- | ---------------------------------------------------- |
| POST   | `/api/auth/signup`       | `SignupInput`                                                                   | `User` (201)                                         |
| POST   | `/api/auth/login`        | `{ email, password }`                                                           | `{ accessToken: string, user: User }` + refresh 쿠키 |
| POST   | `/api/auth/refresh`      | - (refresh 쿠키)                                                                | `{ accessToken: string }` + 새 refresh 쿠키          |
| POST   | `/api/auth/logout`       | - (refresh 쿠키)                                                                | 없음 (세션 폐기, 쿠키 삭제)                          |
| GET    | `/api/users/me`          | -                                                                               | `User`                                               |
| PATCH  | `/api/users/me/consents` | `{ aiAnalysis?: boolean, aiQuiz?: boolean }` (보낸 것만 바꿈, 둘 다 없으면 400) | `User`                                               |
| PATCH  | `/api/users/me/profile`  | `LearningProfileInput`                                                          | `User` (학습 목적·관심 직무)                         |
| DELETE | `/api/users/me`          | `{ password?, confirmText? }`                                                   | 없음 (탈퇴: 내 모든 데이터 삭제)                     |

```ts
SignupInput = {
  email: string, // 서버가 소문자로 바꿔 저장
  name: string, // 1~30자
  password: string,
  passwordConfirm: string, // 서버도 password와 같은지 확인
  agreedTerms: boolean, // true여야 함
  agreedPrivacy: boolean, // true여야 함
  agreedAiAnalysis: boolean,
  agreedAiQuiz?: boolean, // 선택, 없으면 false
  learningPurpose?: LearningPurpose, // 없으면 undecided
  jobField?: JobField | null,
  jobFieldOther?: string | null,
  turnstileToken?: string, // 가입 봇 확인 (서버에 Turnstile 키가 있을 때)
};
User = {
  id: number,
  email: string,
  name: string,
  aiAnalysisConsent: boolean,
  aiQuizConsent: boolean,
  role: "USER" | "ADMIN",
  learningPurpose: "exam_prep" | "self_development" | "work" | "undecided",
  jobField: JobField | null, // 목록은 constants/learning.ts와 백엔드 JobField에 함께 둔다
  jobFieldOther: string | null,
  hasPassword: boolean, // 카카오로만 가입한 계정은 false
};
LearningProfileInput = { learningPurpose, jobField: JobField | null, jobFieldOther: string | null };
```

- 가입 동의 중 이용약관·개인정보 수집은 필수, **AI 대화 분석·AI 퀴즈 처리는 선택**이다. 선택 동의를 안 해도 자료로 공부할 수 있어야 한다.
- 회원가입 화면의 필수 동의는 **"이용약관과 개인정보 수집·이용 안내를 확인했어요" 체크 하나**다(「개인정보 처리 통합 안내서」(2025.7.): 서비스에 꼭 필요한 정보는 동의가 아니라 계약 이행(§15①4)이 근거). 프론트는 이 체크 하나로 `agreedTerms`·`agreedPrivacy`를 함께 `true`로 보낸다. `privacy_agreed_at`은 "안내를 확인한 시각"이라는 뜻이다.
- 이메일은 중복이면 409 + `message`("이미 가입된 이메일이에요.").
- **비밀번호 규칙:** 서버도 프론트와 같은 4조건(8자 이상, 영문, 숫자, 특수문자 포함)으로 검사한다(`PasswordPolicy`). 추가로 BCrypt 한도 때문에 72바이트를 넘으면 거절한다(프론트에는 표시 조건이 없고 서버 `message`로 안내). 거절 사유는 어긴 첫 번째 규칙을 `message`로 준다. 비밀번호는 BCrypt로 해시하고 로그에 남기지 않는다.
- 로그인 실패 `message`는 이메일이 틀렸는지 비밀번호가 틀렸는지 구분하지 않는다("이메일 또는 비밀번호를 확인해 주세요."). 같은 이메일·같은 곳에서 15분에 5번 틀리면 429("로그인 시도가 너무 많아요. N분 뒤 다시 시도해 주세요.").
- **`/api/auth/login`, `/refresh`, `/logout`은 `X-Requested-With: XMLHttpRequest` 헤더가 있어야 한다**(CSRF 방어, 없으면 403). 프론트 Axios 인스턴스가 모든 요청에 붙인다.
- **동의 변경:** 바꾸는 대상은 항상 토큰의 사용자 본인이다. 다시 켜면 동의 시각을 새로 기록한다(이미 같은 값이면 바꾸지 않는다).
  - **AI 대화 분석 철회(`aiAnalysis: false`)는 가져온 대화와 그로부터 계산된 분석 결과를 함께 삭제한다.** 대화·조각·메시지·가져오기 기록과 분석 결과를 동의 변경과 같은 트랜잭션에서 지운다. 과목·학습자료·학습 피드백(`concept_feedback`)은 남는다. 철회한 뒤에는 다시 동의하기 전까지 대화 가져오기가 403이고, 분석은 동의한 사용자의 대화만 읽는다. 프론트는 철회 전에 확인 창으로 "되돌릴 수 없어요"를 보여 주고, 철회하면 이 브라우저에 저장한 "가릴 단어"도 지운다. 동의하지 않은 사용자에게는 대화 가져오기 화면 위에 마이페이지 안내를 보여 준다.
  - AI 퀴즈 처리 동의(`aiQuiz`)는 대화 분석 동의와 따로다(아래 "퀴즈").
- **탈퇴(`DELETE /api/users/me`)는 내 모든 데이터를 지운다:** 본인(토큰의 사용자)만 한다.
  - 비밀번호가 있는 계정은 `password`(틀리거나 비면 400 "비밀번호가 맞지 않아요. 다시 확인해 주세요.", 아무것도 지우지 않음), **카카오로만 가입해 비밀번호가 없는 계정(`User.hasPassword === false`)은 `confirmText: "탈퇴"`**(앞뒤 공백 무시, 틀리면 400 "확인 문구로 \"탈퇴\"를 입력해 주세요.").
  - 계정, 과목, 자료와 그로부터 만든 개념·퀴즈·평가, 퀴즈 결과, 가져온 대화, 분석 결과를 지운다(FK CASCADE, 자료 원본 파일은 커밋 뒤 삭제 작업). refresh 토큰도 모두 지우고 쿠키를 비운다. 그 뒤 예전 access token·refresh 쿠키·같은 비밀번호 로그인은 모두 401. 마지막 관리자는 탈퇴할 수 없다(409). 카카오가 연결된 계정은 카카오 연결도 끊는다(실패해도 탈퇴는 된다).
  - 프론트는 확인 창을 거친 뒤 부르고, 성공하면 화면 데이터와 이 브라우저의 가릴 단어를 지우고 첫 화면으로 간다.
- **탈퇴 뒤 30일 재가입 제한:** 같은 이메일로 30일 동안 `POST /api/auth/signup`이 409와 "탈퇴한 이메일은 30일 동안 다시 가입할 수 없어요. M월 d일 HH:mm 이후에 가입할 수 있어요."(한국 시간)를 준다. 가입 화면은 서버 `message`를 그대로 보여 준다. 서버 키(`REJOIN_HASH_SECRET`)가 없으면 제한하지 않는다.
- **관리자 역할:** `User.role`은 `USER`/`ADMIN`. 가입은 항상 `USER`이고 관리자는 DB에서 직접 지정한다. `/api/admin/**`는 관리자만 열리고(일반 사용자 403, 로그인 안 하면 401), 역할은 요청마다 DB에서 읽는다. 화면에서 관리자 메뉴를 보일지 정하는 데만 쓰고, 권한은 서버가 매번 확인한다.
- 토큰이 만료돼 401이 오면 프론트가 `/api/auth/refresh`를 한 번 부르고 원래 요청을 다시 보낸다(`api/axios.ts`). 갱신에도 실패하면 로그아웃 상태가 되고 로그인 가드가 로그인 화면으로 보낸다.
- 로그인이 필요한 화면은 `router/RequireAuth.tsx` 아래에 둔다. **`VITE_USE_MOCK=true`(팀 기본값)에서는 가드를 건너뛰어** 백엔드 없이 화면을 개발할 수 있다.

### 대화 가져오기 — 저장 구현됨 (확정: Swagger `대화 가져오기`)

화면: `/analysis/import` (학습자료 > AI 대화 가져오기). 프론트가 하는 일(모두 브라우저 안): ChatGPT·Claude 내보내기 zip 읽기 → 내 과목 키워드로 공부한 대화를 과목별로 나누기 → 개인정보 가리기 → **사용자가 체크한 대화만** 서버로 전송. 서버가 받는 것은 아래 `ImportedConversation`뿐이다.

- 원본 파일과 이미지·첨부 내용은 서버로 가지 않는다(대신 `[이미지]`, `[첨부 파일]` 표시만 남는다).
- 사용자가 직접 넣은 "가릴 단어"(학교·회사 등) 목록도 서버로 보내지 않는다. 가린 결과(`[가린 단어]`)만 간다. 목록은 브라우저(localStorage)에만 계정별로 저장하므로 이 목록을 위한 API는 없다.

| 메서드 | 경로                      | 요청 본문            | 응답 `data`                                                                           |
| ------ | ------------------------- | -------------------- | ------------------------------------------------------------------------------------- |
| POST   | `/api/conversations`      | `ConversationImport` | 201 `{ importedCount, importCompleted }` / 422 `{ rejected: RejectedConversation[] }` |
| GET    | `/api/conversations`      | -                    | `ConversationSummary[]` (내 것만, 최근에 가져온 순)                                   |
| DELETE | `/api/conversations/{id}` | -                    | 없음                                                                                  |

```ts
ConversationImport = {
  importId: string,                  // 보내기를 한 번 누를 때 만든 UUID. 나눠 보내는 모든 요청에 같은 값
  requestIndex: number,              // 이 요청의 번호 (1부터)
  requestCount: number,              // 전체 요청 수
  courseId: number,
  conversations: ImportedConversation[]   // 1~50개
}
ImportedConversation = {
  clientId: string,                  // 파일 안의 대화 id. 같은 대화를 다시 보내면 중복 저장하지 않는다
  source: "chatgpt" | "claude",
  title: string,                     // 500자까지 (프론트가 잘라 보낸다)
  createdAt?: string,                // ISO 문자열
  messages: { role: "user" | "assistant", text: string, continued?: boolean }[],  // text는 20,000자까지
  part?: { parentClientId: string, index: number, count: number }  // 나눠 보낸 조각일 때만
}
RejectedConversation = { clientId: string, reason: string }  // reason은 사용자에게 보여 줄 문구
ConversationSummary  = { id, courseId, source, title, createdAt: string | null, importedAt: string, messageCount: number, analysisReady: boolean }
```

- **요청은 전부 저장하거나 전부 거절한다.** 개인정보 패턴이 남은 대화가 하나라도 있으면 그 요청은 아무것도 저장하지 않고 422와 `data.rejected`를 돌려준다(서버가 몰래 고쳐 저장하지 않는다). 프론트는 거기서 멈추고 그 대화를 보여 준다(`SendStep`).
- **가져오기 작업:** 서버는 `requestCount`개 요청을 모두 받아야 작업을 끝난 것(`importCompleted: true`)으로 본다. 앞서 성공한 요청의 대화는 지우지 않지만, 작업이 끝나기 전에는 분석에 쓰지 않는다(`analysisReady: false`). 같은 `importId`·`requestIndex`를 다시 보내면 저장하지 않고 같은 결과를 돌려주므로, 다시 보내기는 처음부터 다시 보내면 된다. 같은 `importId`인데 `requestCount`가 다르면 400.
- **중복:** 같은 대화(`source` + `clientId`, 조각은 `parentClientId` + `index`)는 한 번만 저장한다. 다른 과목으로 다시 보내도 처음 과목을 유지한다. 끝나지 않은 작업에만 있던 대화를 새 작업에서 다시 보내면 새 작업으로 옮겨져 새 작업이 끝날 때 분석 대상이 된다.
- **긴 대화:** 프론트는 요청 한 번이 **1MB**(nginx 기본 한도)를 넘지 않게 묶고, 대화 하나가 이 크기를 넘으면 메시지 단위 조각으로 나눈다. 조각은 순서와 상관없이 여러 요청으로 와도 한 대화로 이어 붙인다. 이미 다 받은 대화를 다르게 나눠 보내면 중복으로 건너뛰고, 다 받지 못한 대화를 다르게 나눠 보내면 422다.
- **긴 메시지:** 20,000자가 넘는 메시지는 프론트가 같은 역할의 메시지 여러 개로 나누고 마지막이 아닌 조각에 `continued: true`를 붙인다. 서버는 이어진 글로 저장하고 개인정보 검사도 이어 붙인 글로 한다. 글자 수는 UTF-16 단위(JS `length`, Java `String.length()`)다.
- **한도:** 요청 2MB(넘으면 413, 크기를 밝히지 않은 요청은 411), 요청당 대화 50개, 메시지당 20,000자, 제목 500자. 대화당 메시지 수는 제한하지 않는다.
- 서버도 검사하는 것: AI 대화 분석에 동의하지 않았으면 403("AI 대화 분석에 동의해야 대화를 가져올 수 있어요."), 내 과목이 아니면 404, `source`·`role` 허용 목록, 같은 대화가 한 요청에 두 번이면 400. 개인정보 재검사 규칙은 프론트 가리기(러스트 WASM `frontend/wasm/conversation-core/src/mask.rs`)와 같다(이름·사용자가 넣은 단어는 서버가 원래 단어를 몰라 검사하지 않는다). 서버 로그에 대화 본문을 남기지 않는다.
- 과목을 지우면 그 과목에 가져온 대화도 지운다(과목 삭제 확인 문구에 적었다). 대화 한 건을 지우면 그 대화를 쓴 분석도 지운다.
- 프론트 미사용: 가져온 대화 목록·삭제 화면(`GET`·`DELETE`).

### 학습 분석 (여러 AI 모델 비교) — 구현됨 (확정: Swagger `학습 분석`)

GPT·Claude·Gemini가 과목 하나의 가져온 대화를 **같은 기준으로 따로** 분석하고, 서버 코드가 개념(= 과목 키워드)마다 의견을 비교한다. 학습 대시보드(`/analysis`)는 이 API(`providers`·`runs/latest`·`runs` 시작)로 그린다(`frontend/src/api/analysis.api.ts`, 타입 `frontend/src/types/analysis.ts`, 목업 `frontend/src/mocks/analysis.ts`).

| 메서드 | 경로                                           | 요청 본문                                         | 응답 `data`                                                 |
| ------ | ---------------------------------------------- | ------------------------------------------------- | ----------------------------------------------------------- |
| GET    | `/api/analysis/providers`                      | -                                                 | `ProviderStatus[]` (모델별 설정 여부, 키는 안 줌)           |
| POST   | `/api/analysis/courses/{courseId}/runs`        | -                                                 | `AnalysisRun` (201). 모델을 기다리므로 수십 초 걸릴 수 있다 |
| GET    | `/api/analysis/courses/{courseId}/runs/latest` | -                                                 | `LatestAnalysis` (아래, 분석한 적이 없으면 404)             |
| GET    | `/api/analysis/runs/{runId}`                   | -                                                 | `AnalysisRun`                                               |
| GET    | `/api/analysis/courses/{courseId}/feedback`    | -                                                 | `ConceptFeedback[]`                                         |
| PUT    | `/api/analysis/courses/{courseId}/feedback`    | `{ concept, feedback, comment?, analysisRunId? }` | `ConceptFeedback` (있으면 바꾼다)                           |
| DELETE | `/api/analysis/feedback/{id}`                  | -                                                 | 없음 (피드백 취소)                                          |

```ts
ProviderStatus = { provider: "gpt" | "claude" | "gemini", model: string | null, configured: boolean }
UnderstandingStatus = "understood" | "partial" | "not_understood" | "insufficient_evidence" // 이해·부분 이해·이해 부족·판단 근거 부족
AnalysisRun = {
  id, courseId, status: "running" | "completed" | "partial" | "failed", promptVersion, createdAt, completedAt,
  input: { conversationCount, messageCount, truncatedMessageCount },
  models: { provider, model, status: "succeeded" | "failed" | "invalid_output" | "not_configured",
            message: string | null /* 실패·미설정 이유 */, judgedConceptCount, droppedEvidenceCount }[],
  concepts: {                       // studyOrder 순서로 온다
    concept: string,
    comparison: { outcome: "unanimous" | "majority" | "disagreement" | "single_model" | "no_result",
                  status: UnderstandingStatus | null, judgedCount, agreeCount, calledCount },
    judgments: { provider, model, status: UnderstandingStatus, reportedStatus: UnderstandingStatus,
                 adjustedReason: string | null, aiReason, aiNeedsCheck, aiRecommendedActivity,
                 evidence: { messageId, conversationId, conversationTitle, role: "user" | "assistant",
                             kind: "user_explanation" | "user_solution" | "user_question" | "ai_explanation",
                             aiNote, excerpt /* 메시지 앞 120자 */ }[] }[],
    feedback: ConceptFeedback | null,
    studyOrder: number,             // 추천 학습 순서 (코드 규칙)
    recommendConfirmQuiz: boolean,  // "이미 알아요"를 남겨 확인 퀴즈를 권함
    quiz: ConceptQuizStats | null,  // 개념별 퀴즈 결과 (아래 "퀴즈")
    mastery: ConceptMastery         // 숙련도 (5-10절)
  }[]
}
ConceptFeedback = { id, courseId, concept, feedback: "already_know" | "want_to_study", comment: string | null,
                    analysisRunId: number | null, updatedAt, nextAction: "confirm_quiz" | "prioritize_study" }
// run: 모델이 하나 이상 판단한 가장 최근 분석(completed·partial)만.
// lastAttempt: run보다 나중에 시작한 분석이 진행 중이거나 결과 없이 끝났으면 그 시도 (없으면 null)
LatestAnalysis = {
  run: AnalysisRun | null,
  lastAttempt: { runId, status: "running" | "failed", startedAt, finishedAt: string | null,
                 message: string | null /* 결과가 없는 이유, 진행 중이면 null */ } | null
}
```

- **모델이 모두 실패한 분석은 이전 결과를 덮지 않는다.** `POST runs`는 그대로 `status: "failed"`인 `AnalysisRun`(201)을 돌려주지만, `runs/latest`의 `run`은 이전에 모델이 판단한 분석이고 실패는 `lastAttempt.status: "failed"`로만 알린다. 실패한 시도도 24시간 분석 횟수에는 센다(제공사가 시간 초과에도 요금을 매길 수 있다). 판단한 모델이 없는 분석은 숙련도 기록(`concept_mastery`)을 남기지 않고, 일부 모델이 실패한 분석(`partial`)은 대화 쪽 근거만 바뀌었으면 기록하지 않는다(모델이 빠져 생긴 변화와 구분할 수 없다. 퀴즈·산식이 바뀌었거나 첫 기록이면 남긴다).
- **분석 중이면 `lastAttempt.status: "running"`.** 새로고침해도 대시보드가 처음부터 "분석 중"을 보여 주고 끝날 때까지 다시 확인한다. 시작한 지 `app.analysis.running-timeout`(10분)이 지나도 끝나지 않은 분석(서버가 중간에 멈춤)은 `failed`로 준다. 진행 중 표시는 과목 단위다(새 분석을 막는 409는 사용자 단위).
- 동의를 철회하면 대화와 함께 분석 결과도 지운다. 대화 한 건을 지우면 그 대화를 쓴 분석도 지운다. 학습 피드백은 사용자가 쓴 기록이라 남긴다.

화면에 보여 줄 때 지킬 것:

- **모델 비교는 정확도·숙련도가 아니다.** "정확도 67%·100%"처럼 보여 주지 말고 "3개 모델 중 2개가 '부분 이해'" 같은 개수로 보여 주기. 실패·미설정 모델이 있으면 `judgedCount`/`calledCount`로 실제 판단한 모델 수를 보여 주기.
- `outcome`: `unanimous`(판단한 모델 모두 같음. 3개면 전원 일치), `majority`(3개 중 2개 = 다수 의견), `disagreement`(모두 다름, `status` 없음), `single_model`(판단한 모델이 하나라 비교 못 함), `no_result`(판단한 모델 없음).
- `insufficient_evidence`(판단 근거 부족)는 `not_understood`(이해 부족)와 다르다. 같은 색·문구로 묶지 않기.
- `ai`로 시작하는 필드만 AI가 쓴 글(보라색 "AI" 표시). 상태 비교·개수·순서는 코드가 정한 값.
- `reportedStatus`와 `status`가 다르면 `adjustedReason`(예: 사용자가 쓴 메시지 근거가 없어 판단 근거 부족으로 봤어요)을 함께 보여 주기.
- 모델이 `not_configured`면 "연결 안 됨"으로 보여 주기(연결된 척하지 않는다). 키가 하나도 없으면 분석 시작이 503이다.
- **피드백은 AI 판단을 바꾸지 않는다.** `already_know` → 확인 퀴즈 권함(`recommendConfirmQuiz`), `want_to_study` → 추천 순서 맨 앞. 피드백을 남겨도 `comparison`·`judgments`는 그대로다. 같은 개념에 다시 PUT하면 바뀌고, DELETE하면 취소된다. 메모(`comment`)는 200자까지. 화면은 학습 대시보드 모델별 판단 아래 "이 판단, 내 생각과 맞나요?"(이미 알아요 / 더 공부할래요 / 선택 취소)이고, 저장·취소 뒤 `runs/latest`를 다시 불러 순서를 받는다. 메모 입력 칸은 없다.
- 오류: 미동의 403, 남의 과목·분석 404, 과목 키워드 없음·분석할 대화 없음 400, 이미 분석 중 409, 24시간에 10번 넘음 429, 설정된 모델 없음 503, 분석 중 동의 철회·대화 삭제 409.
- 개념은 과목 키워드다. 키워드가 없는 과목은 분석할 수 없다(과목 화면에서 키워드를 넣도록 안내).

### 퀴즈 — 구현됨 (확정: Swagger `퀴즈`)

화면: `/quizzes`(목록), `/quizzes/new`(만들기), `/quizzes/{id}`(풀기), `/quizzes/{id}/result`(결과). 올린 본인만 볼 수 있다.

| 메서드 | 경로                                   | 요청 본문               | 응답 `data`                               |
| ------ | -------------------------------------- | ----------------------- | ----------------------------------------- |
| GET    | `/api/quizzes`                         | -                       | `QuizSummary[]` (최근에 만든 퀴즈부터)    |
| GET    | `/api/quizzes/{id}`                    | -                       | `Quiz` (**정답 미포함**)                  |
| POST   | `/api/quizzes/{id}/submissions`        | `{ answers: Answer[] }` | `QuizResult` (201)                        |
| GET    | `/api/quizzes/{id}/submissions/latest` | -                       | `QuizResult \| null` (안 풀었으면 `null`) |

```ts
QuizSummary = { id: number, title: string, courseId: number, materialId: number | null, questionCount: number, lastCorrectCount: number | null }
Quiz        = { id: number, title: string, courseId: number, materialId: number | null, questions: { id: number, text: string, choices: string[] }[] }
Answer      = { questionId: number, choiceIndex: number }
QuizResult  = {
  submissionId: number,
  submittedAt: string,                // ISO 문자열
  correctCount: number,
  totalCount: number,
  results: {
    questionId: number,
    choiceIndex: number,              // 사용자가 고른 보기 (안 고르면 -1)
    correct: boolean,
    correctChoiceIndex: number,       // 제출 뒤에만 알려 준다
    concepts: { conceptId: string, name: string }[], // 이 문제가 다루는 개념 (conceptId는 대시보드 ConceptMastery.conceptId와 같은 값, 이름은 결과 화면 표시용)
    aiExplanation: string | null      // 해설 (AI 글이므로 필드를 분리, 보라색 표시)
  }[]
}
```

- **채점은 서버 코드가 한다.** LLM은 해설(`aiExplanation`)만 쓰고 정오는 정하지 않는다.
- 풀이 전 `Quiz` 응답에 정답이 들어가면 안 된다.
- 남의 퀴즈는 404, 이 퀴즈에 없는 문제·같은 문제를 두 번 보낸 답·범위 밖 보기 번호는 400, 빠진 문제는 `-1`(틀림)로 채점한다.
- 제출 결과는 개념별 퀴즈 결과(`ConceptQuizStats`)로 모여 숙련도(5-10절)에 쓰인다.
- 같은 퀴즈를 여러 번 풀 수 있다. 목록의 `lastCorrectCount`는 가장 최근 제출 기준이다.
- `materialId`: 그 퀴즈를 만든 자료(자료 상세의 퀴즈 탭이 이 값으로 거른다). 약점 개념으로 만든 퀴즈처럼 자료가 없으면 `null`.
- `/submissions/latest`: 결과 화면을 새로고침하거나 목록에서 "결과 보기"로 들어올 때 쓴다.

#### 학습 모드 3가지, AI로 만들기, 주관식·서술형

위 4개 API의 경로와 기존 필드는 그대로 두고 **필드를 더하기만** 했다(요청 `{ questionId, choiceIndex }`도 그대로 받는다). 타입 전체는 `frontend/src/types/quiz.ts`(백엔드 DTO와 같은 모양).

| 메서드 | 경로                                                   | 요청 본문         | 응답 `data`                                         |
| ------ | ------------------------------------------------------ | ----------------- | --------------------------------------------------- |
| POST   | `/api/quizzes`                                         | `QuizCreateInput` | `Quiz` (201, 같은 `requestKey`면 200으로 같은 퀴즈) |
| POST   | `/api/quizzes/{id}/submissions/{submissionId}/grading` | -                 | `QuizResult` (채점 대기 답만 다시 채점)             |
| GET    | `/api/quizzes/concept-stats?courseId=`                 | -                 | `ConceptQuizStats[]`                                |

```ts
QuizCreateInput = {
  requestKey: string,              // 화면에서 만든 UUID. 같은 키로 다시 보내면 새로 만들지 않는다 (실패하면 새 키로)
  courseId: number,
  mode: 'exam_prep' | 'self_development' | 'work',
  questionTypes: ('multiple_choice' | 'short_answer' | 'essay')[], // 1~3개
  questionCount: number,           // 고른 유형 수 이상, 10 이하 (유형별 수는 서버가 나눈다)
  difficulty: 'easy' | 'medium' | 'hard',
  concepts?: string[],             // 과목 키워드 중에서. 없으면 키워드 전체(최대 10)
  examScope?: string,              // 시험 대비만, 200자
  jobField?: string, jobFieldOther?: string, // 업무 활용만. 없으면 내 관심 직무, 그것도 없으면 일반 실무
  materialId?: number,             // 연결할 자료. 본문은 useMaterialContent일 때만 쓴다
  mockExam?: boolean,              // 아래 "생성 상태·커리큘럼·AI 퀴즈 처리 동의"
  checkConceptId?: string,         // 개념 확인 퀴즈 (5-9-2)
  // bloomLevels·useMaterialContent·materialContentConsent: evidence-based-learning.md 8절
}
```

- 더한 필드: `QuizSummary`(+`mode`, `difficulty`, `aiGenerated`, `lastScore`, `lastMaxScore`, `lastPendingCount`), `Quiz`(+`mode`, `difficulty`, `jobField`, `jobFieldOther`, `examScope`, `generatedBy`, `materialContentUsed`, 문제마다 `type`·`scenario`·`role`·`guide`·`criteria`(업무 활용 서술형의 평가 기준 이름만)·`maxScore`), `QuizAnswer`(+`text`, `choiceIndex`는 선택), `QuizResult`(+`score`, `maxScore`, `pendingCount`, `reviewCount`, `mode`, `assessment`, `suggestions`, 문제마다 `type`·`textAnswer`·`score`·`maxScore`·`gradingStatus`·`gradingMethod`·`gradingMessage`·`aiCalledCount`·`aiJudgedCount`·`acceptedAnswers`·`aiModelAnswer`·`rubric`·`aiFeedback`). `correctChoiceIndex`는 객관식이 아니면 `null`. 풀기 전 응답에는 여전히 정답·허용 답·모범 답안·배점별 기준·개념이 없다.
- 학습 목적·관심 직무는 `User`에 있고 마이페이지는 `PATCH /api/users/me/profile`로 바꾼다(위 "인증·마이페이지").
- 학습 분석 응답 `concepts[]`의 `quiz: ConceptQuizStats | null`은 AI 판단과 따로이고 추천 순서를 바꾸지 않는다.
- **채점:** 객관식 코드, 주관식은 허용 답과 정규화 비교 후 다르면 AI 의미 판단, 서술형은 기준마다 AI가 수준만 고르고 점수는 서버가 더한다. AI 판단은 설정된 모델 모두가 따로 하고 과반 의견만 쓴다. `gradingStatus`: `graded` / `needs_review`(의견 갈림, 점수에 안 넣음) / `pending`(모델 없음·동의 없음·한도·실패, 점수에 안 넣음, `grading` API로 다시 채점). 화면은 `gradingMessage`를 그대로 보여 주면 된다.
- **결과 판단 `assessment.status`:** `ready` / `not_ready` / `needs_check`(문항 3개 미만, 대기·확인 필요 답, 한 모델만 채점한 서술형). 서버 코드 규칙이고 숙련도 점수가 아니다. `reasons`는 코드가 센 숫자 문장.
- **오류:** 만들기 403(AI 퀴즈 처리 동의 없음), 400(값), 404(남의 과목·자료), 409(같은 사용자의 만들기가 진행 중, 이미 실패한 키), 429(24시간 20번), 502(모델 응답 실패·형식 오류, 저장 안 함), 503(연결된 모델 없음). 제출 422(AI로 보낼 답에 이메일·전화번호 등 개인정보, `data.questionIds`).
- 모델을 기다리므로 만들기·제출은 오래 걸릴 수 있다(아래 timeout).

#### 생성 상태·커리큘럼·AI 퀴즈 처리 동의

| 메서드 | 경로                                    | 요청 본문 | 응답 `data`                                                                              |
| ------ | --------------------------------------- | --------- | ---------------------------------------------------------------------------------------- |
| GET    | `/api/quizzes/generations/{requestKey}` | -         | `{ status: 'running'\|'succeeded'\|'failed'\|'timed_out', quizId, message, deadlineAt }` |
| GET    | `/api/curriculum/courses/{courseId}`    | -         | `Curriculum` (`frontend/src/types/curriculum.ts`, `flow`는 5-9-1·5-9-2)                  |

- **AI 퀴즈 처리 동의를 따로 받는다.** `User.aiQuizConsent`, 가입 `agreedAiQuiz`(선택, 없으면 false), 마이페이지 `PATCH /api/users/me/consents { aiQuiz }`. 대화 분석 동의(`aiAnalysis`)를 퀴즈로 넓히지 않는다. 동의가 없으면 만들기 403, 주관식·서술형은 `pending`(이유 문구는 `gradingMessage`).
- 더한 필드: `QuizCreateInput.mockExam`(exam_prep만, 20문제까지), `Quiz.mockExam`, `QuizSummary.mockExam`·`lastReviewCount`, 개념별 결과 `correctCount`(채점이 끝나고 만점인 문제 수, 대기·확인 필요는 틀린 것으로 세지 않음)·`lastSubmittedAt`·`ratedCount`·`rateSum`(숙련도에 넣는 문제 수와 문제별 득점률 합, 5-10절).
- 오류 추가: 만들기 504(전체 시간 100초 초과, 저장 안 함), 429(24시간 실제 모델 요청 수 상한), 다시 채점 409(같은 제출을 채점 중)·429(24시간 AI 채점 실행 한도, 답은 그대로)·403(퀴즈 동의 없음)·503(모델 없음).
- timeout: 만들기·제출·다시 채점 모두 115초(`AI_REQUEST_TIMEOUT_MS`, 서버 예산 100초보다 길고 Vercel 프록시 한도 120초보다 짧게). 프록시가 낸 504(서버 응답 형식 없음)는 시간 초과와 같게 다룬다. 만들기를 보낸 키는 탭 sessionStorage에 두고 새로 고치면 상태 API로 결과를 찾는다.

사전·사후평가와 자료 핵심 개념은 5-9절.

## 4. 학습 대시보드

### 학습 대시보드 — 학습 분석 API로 연결함

화면: `/analysis` (학습분석 > 학습 대시보드). 과목을 고르면 그 과목의 `runs/latest`를 보여 주고, "분석하기"로 `POST runs`를 부른다. 위 "학습 분석" 절의 응답을 그대로 쓴다(별도 대시보드 API는 없다).

- 영역은 `summary`(맨 위 요약 칸: 숙련도 분포·모델 의견·사전→사후평가·학습 진도) · `concepts`(공부할 순서대로 본 개념 목록, 줄을 누르면 숙련도 근거·모델별 판단이 펼쳐짐) · `models`(모델 연결 상태와 이번 분석 정보) 3개다. 같은 개념 순서를 여러 번 보여 주지 않으려고 한 목록에 모았다. 백엔드가 배치를 만들게 되면 이 3개 id만 쓴다.
- 키가 없어 모델이 하나도 연결되지 않으면 "연결된 AI 모델이 없어 지금은 분석할 수 없어요"를 보여 주고 분석 버튼을 막는다. 목업(배포 미리보기)에서는 "예시 데이터예요"를 표시한다.
- 오류(403·400·409·429·503)는 서버 `message`를 그대로 보여 준다. 화면에 보일 문장이니 사용자에게 다음 행동을 알려 주는 말로 써 주기.

### 학습 대시보드 동적 배치 — 프론트 준비됨, 서버는 선택

LLM은 화면 코드를 만들지 않고, 정해진 영역의 순서·크기·이유만 JSON으로 준다. 프론트는 이 JSON을 zod로 검사해서 통과할 때만 그 배치로 보여 주고, 실패하거나 없으면 기본 배치로 보여 준다(`frontend/src/utils/layout/dashboardLayout.ts`). **지금 백엔드는 배치를 주지 않아 실제 화면은 항상 기본 배치다**(목업에만 예시 배치가 있다). 동적 배치를 살리려면 `AnalysisRun`에 `layout` 필드를 붙이면 된다(없으면 기본 배치, backend `CLAUDE.md`대로 서버에서도 1차 검증):

```ts
layout = {
  aiSummary: string, // 전체 배치 이유 (AI), 120자까지
  basis: {
    // 배치를 만들 때 AI에게 준 숫자 (코드가 센 값)
    notUnderstoodCount: number, // 다수 의견이 '이해 부족'인 개념 수
    disagreementCount: number, // 의견이 갈린 개념 수
    conversationCount: number, // 분석한 대화 수
  },
  items: [
    {
      component: "summary" | "concepts" | "models",
      order: number, // 1부터, 겹치지 않게
      size: "wide" | "normal",
      aiReason: string, // 이 위치·크기인 이유 (AI), 80자까지
    },
  ],
};
```

- 배치에서 빠진 영역은 프론트가 기본 순서로 뒤에 붙이므로 모든 영역을 다 넣지 않아도 된다.
- **AI에게는 코드가 계산한 숫자만 주고 이유를 쓰게 하기.** 프론트는 이유 옆에 `basis` 숫자를 같이 보여 주므로, AI가 숫자를 지어내면 사용자가 바로 알아챈다.
- **매번 AI를 부르지 않기.** 분석을 새로 할 때 한 번 만들어 그 분석 결과와 함께 저장한다. `basis`도 그때 값을 저장해야 이유와 숫자가 어긋나지 않는다.

## 5. 정해 주면 프론트가 맞출 것

- **자료 올리기:** HWP·HWPX 추가 시점(텍스트 추출 후보 hwplib·hwpxlib), 원본의 장기 보관 기간·자동 삭제(루트 `CLAUDE.md` 미결 사항, `TODO(미결)`).
- **사용자별 호출 한도의 숫자:** 구조는 있다(`docs/backend-guide.md` 4절). 값은 근거 없는 임시값이라 실사용 뒤 정한다(`application.yaml`의 `TODO(미결)`). 모델 이름은 운영 설정(`*_MODEL`)이다.
- **동적 배치:** 분석 결과에 `layout`을 줄지(4번 "학습 대시보드 동적 배치"). 지금은 프론트 기본 배치.
- **동의 철회 때 `concept_feedback`:** 지금은 사용자가 쓴 기록이라 남긴다. 함께 지울지 미결.
- **(선택) 가입 오류 문구:** `SignupRequest`의 "개인정보 수집·이용에 동의해 주세요."를 "개인정보 수집·이용 안내를 확인해 주세요."로(화면은 확인 체크 하나다, 위 "인증·마이페이지"). 필드 이름은 바꾸지 않는다.
- **개인정보처리방침(`/privacy`):** 서버가 실제로 하는 처리(저장 항목, 보관·삭제, 토큰, 로그에 남기는 것, LLM·호스팅 서비스)를 기준으로 쓴다(`frontend/src/pages/privacy/PrivacyPage.tsx`, 남은 `TODO(미결)`은 그 파일 위쪽). 서버 동작·LLM 제공사·호스팅·자료 원본 보관이 바뀌면 같은 PR에서 처리방침도 고친다.

## 5-9. 탈퇴·사전·사후평가·자료 핵심 개념

탈퇴는 위 "인증·마이페이지". 확정: Swagger `사전·사후평가`(핵심 개념 API도 같은 태그).

| 메서드 | 경로                           | 요청 본문                             | 응답 `data`                                                |
| ------ | ------------------------------ | ------------------------------------- | ---------------------------------------------------------- |
| POST   | `/api/assessments`             | `AssessmentCreateInput`               | `Quiz` (201, 풀기·결과는 `/api/quizzes/{id}` 그대로)       |
| GET    | `/api/assessments/comparison`  | - (`?courseId=` 또는 `?materialId=`)  | `AssessmentComparison`                                     |
| GET    | `/api/assessments/ai-status`   | -                                     | `QuizAiStatus` (키 값 없음, `materialContentEnabled` 포함) |
| GET    | `/api/materials/{id}/concepts` | -                                     | `MaterialConcept[]` (안 뽑았으면 빈 목록)                  |
| POST   | `/api/materials/{id}/concepts` | `{ materialContentConsent: boolean }` | `MaterialConcept[]` (새로 뽑은 개념으로 바꿈)              |

타입은 `frontend/src/types/assessment.ts`, `types/material.ts`(`MaterialConcept`), `types/quiz.ts`(`Quiz.assessmentKind`·`preAssessmentId`·`materialContentUsed`·`materialContentNote`·`topicSource`, `MaterialContentNote`).

- **평가 = 퀴즈 행.** `quiz.assessment_kind`(PRE/POST), `pre_assessment_id`(사후 → 사전), `material_content_used`, `concept_source`(MATERIAL/COURSE). 풀기·채점·결과는 퀴즈와 같다. 과목에 연결된 자료만(아니면 400).
- **사후평가는 최신 사전평가를 그대로 따른다:** 같은 개념·같은 개념 출처·유형별 문항 수·문항 수·난이도·모드. 사후 요청의 유형·수·난이도는 무시한다. 문제가 겹치지 않게 사전평가 문제 글을 `avoidQuestions`로 AI에 함께 보낸다. 사전평가가 없으면 400.
- **평가할 개념:** 자료에서 뽑은 개념이 있으면 그것(`topicSource: 'material'`), 없으면 과목 키워드(`'course'`). 풀기 응답은 문제별 개념을 싣지 않으므로 평가 단위 출처만 `topicSource`라는 이름으로 준다.
- **비교 (`GET /comparison`):** 그 과목(또는 자료)의 **최신 제출된 사전평가 vs 그 사전평가에 연결된 최신 사후평가**. 각 평가의 가장 최근 제출만, **채점이 끝난 답만** 더한다(대기·확인 필요는 틀린 답으로 세지 않고 빼며 `pendingCount`·`reviewCount`로 알린다). `overallDelta`·`concepts[].delta`는 정답률 차이(%p, 반올림), `concepts[].status`는 서버 규칙(기준 `app.quiz.assessment.concept-min-rate` 0.6): improved / declined / unchanged / still_weak(사후도 기준 미만) / weak·ok(사후 전) / not_taken. `comparability`는 개념 집합·문항 수·유형별 수·난이도가 같은지와 주의 문장(`notes`: 문제는 새로 만든 것, 미채점 답, 자료 본문 사용 여부 차이 등). 숙련도 점수가 아니다.
- **자료 본문 외부 전송 (`app.material-content.enabled`, 환경변수 `MATERIAL_CONTENT_ENABLED`, 기본 false):** 꺼져 있으면 `useMaterialContent: true` 평가 요청과 핵심 개념 뽑기가 403이고, 평가는 개념 목록으로만 만든다. 켜져 있어도 요청마다 `materialContentConsent: true`(없으면 400)와 AI 퀴즈 처리 동의(없으면 403)가 있어야 한다. 제공사·국가·보관 기간과 처리 근거를 처리방침에 확정하기 전에는 켜지 않는다.
- **본문 읽기 (`MaterialTextExtractor`, 외부 서비스 없음, PDF는 서버 안의 PDFBox):** TXT·MD(UTF-8), PPTX(슬라이드 글 칸, `[슬라이드 N]`), PDF(쪽마다 `[p. N]`, 최대 100쪽·12,000자, 넘으면 잘림). 글이 없는 PDF(스캔본) → `NO_TEXT`(OCR 미지원), 암호 PDF → `ENCRYPTED`, 손상 → `FAILED`. 본문에 개인정보 패턴(IP 제외 규칙)이 있으면 보내지 않는다(`PERSONAL_INFO`). 평가 만들기는 이때 본문 없이 만들고 `materialContentNote`로 이유를 준다(`materialContentUsed=false`). 뽑은 글은 저장하지 않는다.
- **핵심 개념 뽑기:** AI가 `{name, description, quote}` 후보를 내면, 서버가 **이름과 근거 문장(quote)이 정규화한 자료 본문에 실제로 있는지** 확인해 통과한 것만 저장한다(최대 10개, 이름 20자). 근거 위치는 quote 앞의 마지막 `[p. N]`/`[슬라이드 N]` 표시로 서버가 정한다. 과목 키워드와 같은 개념이면 `courseKeyword: true`. 통과한 개념이 없으면 502이고 아무것도 저장하지 않는다. 읽지 못한 자료는 422(`data.reason`: NO_TEXT·ENCRYPTED·EMPTY·FAILED·NOT_SUPPORTED), 개인정보 422, 남의 자료 404, 모델 없음 503, 24시간 한도(만들기 한도와 같은 수, 모델 요청 상한 공유) 429. 자료를 지우면 개념도 지워진다(FK CASCADE).
- **AI가 하나뿐일 때:** 만들기는 그 모델로, 채점도 그 모델 하나로 한다. 판단이 하나뿐이면 다수결이 아니라 `SINGLE_MODEL`("AI 모델 하나만 판단한 결과예요")로 표시하고 결과 판단은 "추가 확인 필요"(needs_check)로 본다. 화면은 "AI 1개만 판단 (다수결 아님)". 키와 모델 이름이 둘 다 있어야 그 모델을 쓰고, 하나도 없으면 만들기 503·`ai-status.available=false`.
- 프론트: `pages/assessments/AssessmentPage.tsx`(`/assessments`), `pages/materials/components/MaterialConceptPanel.tsx`(평가 화면·자료 상세 "핵심 개념" 탭 공용), `pages/analysis/components/AssessmentComparisonPanel.tsx`(대시보드 비교).

## 5-9-1. 검색·커리큘럼 4단계·개념 자세한 설명

- **검색 `GET /api/search?q=`** (Swagger `검색`, 로그인 필요):
  - 찾는 곳: 내 과목(이름·키워드), 내 자료(제목·파일 이름), 자료 핵심 개념(이름), 공지(제목, 5개까지)
  - 규칙: 대소문자·띄어쓰기 무시. 과목 이름이 맞으면 그 과목의 자료도 `matchedBy: 'course'`로 준다. 자료 본문은 찾지 않는다
  - 오류: 빈 검색어·50자 초과는 400
  - 프론트: `types/search.ts`, `api/search.api.ts`, `pages/search/SearchPage.tsx`. 목업 `mocks/search.ts`는 같은 규칙(`utils/search.ts`)
- **커리큘럼 응답의 `flow`** (`GET /api/curriculum/courses/{id}`):
  - 4단계: 사전 테스트 → 핵심 개념 학습 → 개념별 확인 퀴즈 → 사후 평가
  - 담는 값: `currentStep`, `stages[]`(done/current/upcoming + 코드가 센 요약), `preTest`·`postTest`(사전·사후 비교 재사용), `concepts[]`(사전 결과·확인 퀴즈 결과·사후 결과·공부할 자료·`priority`), `overallDelta`
  - 확인 퀴즈는 사전평가 제출 뒤에 푼 일반 퀴즈(평가 제외)만 센다
  - 화면: `pages/curriculum/CurriculumPage.tsx`. 개념별 순서·이유는 "개념별 복습 우선순위와 근거 보기"로 접어 둔다. `?courseId=`로 과목을 고른다
- **개념 자세한 설명 `POST /api/materials/{id}/concepts/study-notes`** `{ conceptId, materialContentConsent }` → `MaterialConcept`
  - 개념 하나씩 만든다(응답 길이·비용 때문)
  - 담는 값: 자세한 설명, 자료와의 연결, 연결 문장(서버가 본문에서 확인한 것만, 아니면 null), 중요한 이유, 예시
  - 보내기 조건·오류는 핵심 개념 뽑기와 같다. 없는 개념 404, 그 사이 개념을 다시 뽑았으면 409
  - 개념 이름·근거는 바꾸지 않아 사전·사후평가 개념 체계가 유지된다. 하루 40번(`quiz_ai_call.purpose = CONCEPT_NOTE`)이고, 모델 요청 수 상한도 함께 적용된다
  - `MaterialConcept`과 자료 상세 `concepts[]`의 `studyNote`(없으면 null)
- **자료 상세:** `?tab=concepts&concept=<conceptId>`로 오면 핵심 개념 탭에서 그 개념을 펼친다(커리큘럼 "개념 보기", 검색 결과).

## 5-9-2. 개념별 학습 기록·확인 퀴즈·복습 순환

- 표 `concept_study_progress`: 사용자·자료·개념 키마다 한 행. 회원·과목·자료 삭제 시 CASCADE. 확인 퀴즈는 `quiz.check_concept_key`·`check_concept_name`.
- **개념은 (자료, 개념 키)로 가리킨다.** `material_concept.id`는 "다시 뽑기" 때 바뀌어서 쓰지 않는다. 같은 이름도 자료가 다르면 다른 기록이다.
- **학습 상태 `GET /api/materials/{id}/concepts/progress`** → `ConceptStudy[]` (Swagger `개념 학습 기록`)
  - 학습 기록(`studied`, `firstCompletedAt`, `lastStudiedAt`, `studyCount`)과 확인 퀴즈 결과(`latestCheck`, `checkHistory`)를 따로 담는다
  - `openCheckQuizId`: 만들었지만 아직 안 푼 확인 퀴즈
  - `status`는 둘을 합친 코드 규칙 결과다: `not_studied` / `studied` / `understood` / `review_needed` / `needs_check`
- **학습 완료 `POST /api/materials/{id}/concepts/progress`** `{ conceptId }` → `ConceptStudy`
  - 여러 번 보내도 기록은 하나다
  - `review_needed`에서 다시 보낼 때만 `lastStudiedAt`과 `studyCount`가 바뀐다
  - 학습 완료는 이해 여부·숙련도·대화 분석을 바꾸지 않는다
  - 오류: 남의 자료·없는 개념 404, 과목 없는 자료 400
- **판정 규칙 (`ConceptStudyRules`):** 확인 퀴즈의 가장 최근 제출이 마지막 학습 완료와 같거나 그 뒤면 그 결과로 정한다.
  - 대기·확인 필요 답이 있거나, 채점이 끝난 문제가 2개(`app.quiz.assessment.confirm-min-graded`, 기본 2) 미만이면 `needs_check`
  - 정답률이 0.6(`app.quiz.assessment.concept-min-rate`) 이상이면 `understood`, 미만이면 `review_needed`
  - 채점이 끝나지 않은 답은 틀린 답으로 세지 않는다
- **개념 확인 퀴즈:** `POST /api/quizzes`의 `checkConceptId`(`materialId` 필수).
  - 그 개념 하나만 다루고, 다른 개념 문제는 파서가 거절한다
  - 제목은 "<개념> 확인 퀴즈". 사전·사후평가·모의시험과 함께 쓰면 400, 없는 개념은 404
  - 풀기·채점·다시 채점은 기존 API를 그대로 쓴다
  - `Quiz.checkTopic { materialId, topicId, name }`. 풀기 응답에는 `concept` 글자를 쓰지 않는다는 계약 때문에 `topic`이라는 이름을 쓴다
- **커리큘럼 `flow`:**
  - `concepts[].study`(자료 개념의 `ConceptStudy`, 과목 키워드뿐이면 null)와 `progress`(학습 진행률 등)
  - 지금 단계 규칙: 먼저 공부할 개념 중 학습 전·복습 필요가 있으면 개념 학습, 학습 완료 뒤 확인 퀴즈를 안 풀었으면 확인 퀴즈, 그 밖은 사후 평가
  - 과목 키워드뿐인 개념은 일반 퀴즈 결과로 본다
  - 사후 평가는 막지 않고, 남은 개념 수를 요약에 쓴다
  - **단계 상태:** `stages[].status`의 `done`은 지금 단계와 따로 센다. 앞 단계가 남아 있어도 끝낸 단계는 `done`이다(예: 개념 학습이 남았는데 사후 평가를 제출했으면 STEP 2 `current`, STEP 4 `done`). 사전·사후 평가는 제출, 개념 학습은 먼저 공부할 개념 중 학습 전·복습 필요가 없음, 확인 퀴즈는 먼저 공부할 개념을 모두 확인 퀴즈까지 마침(지금 단계 규칙과 같은 판정). 과목 키워드뿐인 개념은 사전 테스트 뒤 퀴즈로만 보므로 사전 테스트 전에는 끝낸 것으로 보지 않는다. `currentStep`은 끝내지 않은 첫 단계다
- **프론트:**
  - 파일: `types/study.ts`, `api/study.api.ts`, `utils/conceptStudy.ts`, `components/common/StudyStatusBadge.tsx`, `pages/materials/components/ConceptStudyActions.tsx`, `pages/analysis/components/StudyProgressPanel.tsx`(대시보드)
  - 확인 퀴즈 모드: `/quizzes/new?checkMaterialId=&checkConceptId=&concept=`
  - 결과 화면: "개념으로 돌아가기"·"커리큘럼으로 돌아가기"
  - 체험 모드 기록은 목업이 브라우저 저장소(`msg-mock-study`)에만 둔다(서버로 보내지 않음)

## 5-10. 숙련도 점수 산식 설계안과 참고 문헌

**상태:** 아래 설계안이 초기값으로 확정돼 구현돼 있다(루트 `CLAUDE.md` 미결 사항). 숫자(가중치·기준·경계)는 근거 없는 초기 가정이라 **전부 설정값**(`app.mastery.*`)으로 두고 사용자 테스트 뒤 조정한다.

- 계산: `analysis/application/MasteryCalculator`(+`MasteryProperties`, 단위 테스트가 아래 계산 예시와 같은 값을 확인). 기록·흐름은 `docs/backend-guide.md` 8절.
- 응답: 분석 응답 `concepts[].mastery`(프론트 타입 `ConceptMastery`) = `level`·`score`·`evidenceWeight`·`quizGradedCount`·`quizRate`·`conversationCount`·`conversationStatus`·`formulaVersion`·`quizNeeded`(근거 부족일 때 단계가 나오려면 더 풀 퀴즈 문제 수, 충분하면 null)·`history`.
- `history`: `recordedAt`·`level`·`quizGradedCount`·`quizRate`·`conversationCount`·`conversationStatus`·`formulaVersion`, 오래된 것부터 최근 30개. 분석 실행·`runs/latest` 때 마지막 기록과 단계·근거가 다르면 `concept_mastery`에 한 줄 남기고, `runs/{id}`는 기록하지 않는다.
- 퀴즈 근거는 채점이 끝난 문제마다 s = 그 문제 점수 ÷ 그 문제 만점으로 센다(만점이 0인 답은 뺀다). 그래서 `quizRate`는 문제별 평균 득점률이고 퀴즈 결과의 점수 합 비율(`score ÷ maxScore`)과 다를 수 있다. 개념별 퀴즈 결과(`ConceptQuizStats`)의 `ratedCount`·`rateSum`이 이 계산에 쓰는 문제 수와 득점률 합이다.
- 화면은 대시보드 개념 목록과 개념 근거에 단계·근거만 보여 준다(숫자 점수 없음).

### 원칙 (이미 정한 것)

- 점수는 **명시적 규칙의 코드**가 계산한다. LLM은 근거를 설명만 한다(루트 `CLAUDE.md`, `backend-design.md` 7번 "숙련도").
- 근거가 부족한 것을 이해 부족으로 바꾸지 않는다. 대화만으로 단정하지 않는다.
- "정확도"·"진단" 같은 표현을 쓰지 않는다. 가중치는 **근거 없는 초기 가정**이고 사용자 테스트 뒤에 조정한다.
- 산식이 바뀌면 `concept_mastery.formula_version`으로 이전 결과와 구분한다. 버전은 `앞 번호-지문`(예: `2-a1b3c9d2`)이다. 앞 번호(`app.mastery.formula-version`, 지금 `2`)는 식 모양을 바꿀 때만 올리고, 숫자 설정만 바꾸면 뒤의 지문(숫자 설정 전부의 SHA-256 앞 8자리)이 저절로 바뀐다. 버전이 다른 기록끼리는 단계 변화("한 단계 올랐어요")를 보여 주지 않는다.

### 계산 (개념 하나마다)

1. **근거를 모은다.** 개념마다 근거 한 건은 값 `s`(0~1)와 가중치 `w`를 가진다.

| 근거                                    | 값 `s`                   | 가중치 `w` (설정값) |
| --------------------------------------- | ------------------------ | ------------------- |
| 퀴즈 객관식 정답 / 오답                 | 1 / 0                    | 1.0                 |
| 퀴즈 주관식·서술형                      | 채점된 점수 ÷ 만점 (0~1) | 1.0                 |
| 대화: 스스로 활용 (`understood`에 해당) | 1                        | 0.5                 |
| 대화: 재설명 요청 (`partial`에 해당)    | 0.3                      | 0.5                 |
| 대화: 이해 못 함 (`not_understood`)     | 0                        | 0.5                 |

- 퀴즈 가중치를 대화의 2배로 둔 이유: 퀴즈는 직접 확인한 결과이고 대화는 간접 신호다.
- **채점 대기·확인 필요 답은 근거에서 뺀다**(사전·사후평가 비교와 같은 규칙). 몇 개를 뺐는지는 화면에 숫자로 보여 준다.
- 대화 근거는 사용자가 쓴 근거 메시지가 있는 대화 수만큼, 모델 비교가 일치한 개념 상태(`UnderstandingStatus`)로 센다. 모델이 여럿이어도 같은 대화는 한 번만 센다. 메시지 단위로 셀지는 미결(아래 "열린 질문").

2. **점수 = (Σ w·s + 1) ÷ (Σ w + 2).** 근거가 한두 개일 때 점수가 0이나 1로 튀지 않게 하는 보정이다(일반 통계의 라플라스 보정과 같은 꼴).
3. **근거 부족:** `Σ w`가 기준 `T`(제안 3.0, 설정값) 미만이면 점수를 내지 않고 `insufficient_evidence`("근거 부족")로 둔다.
4. **단계:** 점수 < 0.4 보완 필요 / 0.4 이상 0.7 미만 보통 / 0.7 이상 강점(제안, 설정값).
5. **화면은 숫자 대신 4단계**(근거 부족·보완 필요·보통·강점)와 **근거 목록**을 함께 보여 준다(예: "퀴즈 3문제 중 2개 정답, 대화 2건"). 근거 부족은 이해 부족과 다른 모양으로 그린다(`UnderstandingBadge`와 같은 원칙).
6. **유형 B(자료로 시작)는 퀴즈만, 유형 A(대화로 시작)는 대화만으로도** 같은 식이 그대로 동작한다.

### 계산 예시 (단위 테스트로 써도 된다. `T`=3.0, 기준 0.4·0.7일 때)

| 근거                                         | Σ w·s | Σ w | 점수               | 단계                  |
| -------------------------------------------- | ----- | --- | ------------------ | --------------------- |
| 퀴즈 3문제 중 2개 정답                       | 2     | 3   | (2+1)÷(3+2) = 0.60 | 보통                  |
| 퀴즈 4문제 중 4개 정답                       | 4     | 4   | (4+1)÷(4+2) ≈ 0.83 | 강점                  |
| 퀴즈 4문제 중 1개 정답 + 대화 이해 못 함 2건 | 1     | 5   | (1+1)÷(5+2) ≈ 0.29 | 보완 필요             |
| 대화 이해 못 함 2건만                        | 0     | 1   | Σ w < 3            | 근거 부족 (점수 없음) |
| 퀴즈 1문제 정답만                            | 1     | 1   | Σ w < 3            | 근거 부족 (점수 없음) |

### 나중으로 미룬 것 (2단계 후보)

- 최근 근거에 더 큰 가중치를 주는 **시간 감쇠(반감기)**. 지금은 넣지 않는다.
- 문항·학습자 난이도를 따로 추정하는 방식, 매개변수를 데이터로 학습하는 방식(아래 BKT). 데이터가 쌓인 뒤에 검토한다.

### 정해야 하는 값 (설정값으로 분리)

대화 신호별 값(1 / 0.3 / 0)과 가중치 비율(1.0 : 0.5), 근거 부족 기준 `T`, 단계 경계(0.4·0.7), 시간 감쇠를 둘지. 전부 초기 가정이다.

### 참고 문헌 — 확인한 것과 쓰는 법

**"참고했다"까지만 쓴다. 이 산식은 아래 모델을 구현한 것이 아니라 우리가 만든 단순한 규칙이다.** 보고서·README에 "BKT를 적용했다" 같은 표현을 쓰지 않는다. 아래는 웹 검색으로 서지 정보를 확인한 것이다(확인일 2026-11-04, 논문 전문은 읽지 않았고 초록·서지 정보까지만 확인).

| 자료                                                                                                                                                                                                                                                                              | 우리 설계와의 관계                                                                                                                                                        |
| --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Khan Academy 숙련도 단계(Attempted·Familiar·Proficient·Mastered). [고객센터 "How do Khan Academy's Mastery levels work?"](https://support.khanacademy.org/hc/tr/articles/5548760867853)                                                                                           | 점수 숫자 대신 **단계**로 보여 주는 방향의 참고. 정답률 구간(출처마다 70~85%, 70~99%로 다름)은 인용하지 않는다. 영어판 주소는 확인하지 못했으니 인용할 때 열어서 확인한다 |
| Corbett, A. T. & Anderson, J. R. (1995). Knowledge Tracing: Modeling the Acquisition of Procedural Knowledge. _User Modeling and User-Adapted Interaction_ 4(4): 253–278. [DOI 10.1007/BF01099821](https://link.springer.com/doi/10.1007/BF01099821) (Bayesian Knowledge Tracing) | 학생이 개념을 아는지를 확률로 추정하는 고전 모델. 매개변수를 데이터로 학습해야 해서 **2단계 후보**. 이미 배운 것은 잊지 않는다고 가정하는 한계가 알려져 있다              |
| Pelánek, R. (2016). Applications of the Elo rating system in adaptive educational systems. _Computers & Education_ 98: 169–179. [DOI 10.1016/j.compedu.2016.03.017](https://doi.org/10.1016/j.compedu.2016.03.017)                                                                | 학생 답을 "학생과 문항의 대결"로 보고 학생 실력과 문항 난이도를 함께 추정. 단순하고 튼튼하다고 소개됨. 문항이 많아야 해서 지금은 쓰지 않는다                              |
| Settles, B. & Meeder, B. (2016). A Trainable Spaced Repetition Model for Language Learning. _ACL_ 1848–1858. [DOI 10.18653/v1/P16-1174](https://doi.org/10.18653/v1/P16-1174) (Duolingo, Half-Life Regression)                                                                    | 기억이 유지되는 "반감기"를 추정. 시간 감쇠를 도입할 때의 참고(2단계). 한 외부 분석 글은 공개 데이터에서 예측력이 약하다고 주장한다(비공식 글 하나라 참고만)               |
| Woźniak, P. (1990). SM-2 알고리즘(SuperMemo). [SuperMemo Guru "Algorithm SM-2"](https://www.supermemo.guru/wiki/SuperMemo_2)                                                                                                                                                      | 복습 간격을 정하는 간격 반복 알고리즘(Anki 등이 변형해 사용). 복습 시점 추천을 만들 때 참고(2단계)                                                                        |

- **문항 반응 이론(IRT)** 등 다른 문헌은 확인하지 않았으므로 인용하지 않는다.
- 위 표의 "관계" 칸 문장은 우리 설계에 대한 설명이고 논문의 주장이 아니다. 논문 내용을 인용할 때는 원문을 열어서 확인한다.

### 열린 질문 (백엔드와 정할 것)

- 대화 신호를 메시지 단위로 셀지(지금은 개념 단위 판단을 근거 메시지가 있는 대화 수만큼 센다, `AnalysisQueryService`의 `TODO(미결)`).

## 5-11. 카카오 로그인

비밀번호 로그인은 그대로 두고 카카오 로그인을 더했다. 확정: Swagger `소셜 로그인`. 브라우저가 주소로 **직접 이동**하는 흐름이라 axios로 부르지 않는 주소가 섞여 있다.

| 주소 | 누가 부르나 | 결과 |
| ---- | ----------- | ---- |
| `GET /api/auth/social/providers` | 로그인·가입 화면(axios) | `["kakao"]` 또는 `[]`(서버에 카카오 키가 없을 때, 체험·PR 미리보기). 목록에 있을 때만 버튼을 보여 준다 |
| `GET /api/auth/kakao/start?returnTo=/경로` | 버튼 → `window.location.assign` | 카카오 로그인·동의 화면으로 302. `returnTo`는 `/`로 시작하는 사이트 안 경로만 쓰고 아니면 `/` |
| `GET /api/auth/kakao/callback` | 카카오가 브라우저를 돌려보냄 | 아래 셋 중 하나로 302 |
| `GET /api/auth/social/pending` | 가입 마무리 화면(axios) | `{ provider: "kakao", email, nickname \| null }`. 가입 마무리 쿠키가 없거나 10분이 지났으면 401 "가입 정보가 만료됐어요. 카카오 로그인을 다시 해 주세요." |
| `POST /api/auth/social/link` | 계정 연결 화면(axios, `X-Requested-With` 필요) | 본문 `{ password }`. 같은 이메일의 비밀번호 계정 비밀번호를 확인하고 카카오를 연결한 뒤 **로그인과 같은 응답** + Refresh 쿠키. 틀리면 로그인과 같은 401 "이메일 또는 비밀번호를 확인해 주세요."·429(이때 쿠키는 남아 다시 시도 가능), 쿠키 없음·만료 401, 그새 다른 카카오가 연결됐거나 계정이 없으면 409 |
| `POST /api/auth/social/signup` | 가입 마무리 화면(axios, `X-Requested-With` 필요) | 본문 `{ name, agreedTerms, agreedPrivacy, agreedAiAnalysis, agreedAiQuiz, learningPurpose?, jobField?, jobFieldOther? }`(일반 가입에서 이메일·비밀번호·Turnstile만 뺀 것, 오류 문구도 같음). 성공하면 **로그인과 같은 응답** `{ accessToken, user }` + Refresh 쿠키. 같은 이메일이 이미 있거나 이미 가입을 마쳤으면 409 "이미 가입된 이메일이에요.", 탈퇴 뒤 30일 안이면 409(재가입 제한 문구) |

콜백이 보내는 화면 (`FRONTEND_BASE_URL` 기준):

- `/auth/social/done?returnTo=…` — 이미 가입한 사용자. Refresh 쿠키가 심어져 있으니 화면이 `/api/auth/refresh`를 한 번 불러 Access Token을 받고 `returnTo`로 간다.
- `/auth/social/link?returnTo=…` — 카카오가 확인한 이메일과 같은 이메일의 **비밀번호 계정**이 이미 있다. 자동으로 연결하지 않는다(비밀번호 가입은 이메일 주인을 확인하지 않아 남이 미리 만든 계정일 수 있다). `pending`으로 이메일을 보여 주고 그 계정의 비밀번호를 받아 `link`를 부른다. 비밀번호를 모르면 그냥 이메일·비밀번호 로그인으로 가면 된다.
- `/signup/social?returnTo=…` — 처음 온 사용자. 가입 마무리 쿠키(HttpOnly, 화면 스크립트는 못 읽음)가 있으니 `pending`으로 이메일·닉네임을 보여 주고 약관 동의를 받아 `signup`을 부른다. **이때까지 서버 DB에는 아무것도 저장하지 않는다.**
- `/login?socialError=코드` — 실패. 코드: `state`(시간이 지났거나 다른 창에서 돌아옴), `cancelled`(동의 화면에서 취소, 문구 없이 조용히), `email_required`(이메일 제공 동의 안 함), `email_unverified`(카카오에서 인증 안 된 이메일), `rejoin_restricted`(탈퇴 30일 안), `link_conflict`(그 이메일 계정에 다른 카카오가 이미 연결됨), `failed`(카카오 장애 등), `unavailable`(서버에 카카오 키 없음). state 오류가 아니고 돌아갈 화면이 첫 화면이 아니면 `&returnTo=경로`도 붙인다(로그인 화면은 로그인 가드가 넘긴 값이 없을 때 이 값으로 돌아간다). `email_required`·`email_unverified`·`rejoin_restricted`·`link_conflict`로 거절할 때는 카카오 동의로 이미 생긴 카카오 쪽 연결도 서버가 끊는다(실패해도 같은 코드로 보낸다).

- 같은 이메일로 비밀번호 가입한 회원이 카카오로 들어오면, 카카오가 **확인한 이메일**이어도 바로 연결하지 않고 위 계정 연결 화면에서 비밀번호를 확인한 뒤 연결한다. 한 번 연결하면 다음부터는 카카오로 바로 로그인된다.
- `returnTo`는 `useSearchParams().get('returnTo')` 값을 그대로 `safeReturnTo`에 넣고 다시 디코드하지 않는다. 이동은 `navigate()`로만 한다(두 번 디코드하면 `/%2F%2Fevil.com`이 `//evil.com`이 된다).
- `User.hasPassword`(로그인·내 정보·가입 응답 모두): 카카오로만 가입한 계정은 `false`이고 비밀번호 로그인은 "이메일 또는 비밀번호를 확인해 주세요."로 실패한다. 탈퇴 요청 모양은 위 "인증·마이페이지" 참고.
- 카카오 디자인 가이드: 버튼 배경 `#FEE500`, 검정 말풍선 심볼, 글자 `rgba(0,0,0,0.85)` "카카오 로그인", 모서리 12px.

## 6. 이 문서 고치는 법

- 백엔드가 API를 확정하거나 바꾸면 Swagger를 기준으로 하고, 이 문서의 해당 부분을 같은 PR에서 고친다. 날짜·PR 번호·마이그레이션 번호 같은 경과는 쓰지 않는다(기록은 git과 PR).
- 프론트가 새 화면에서 API가 필요해지면 여기에 제안을 추가하고 PR에 적는다.
