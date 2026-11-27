# 낡은 문서·주석 고치기 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 코드·확정 사항과 어긋난 문서 문장과 `TODO(미결)` 주석을 지금 코드 기준으로 맞춘다.

**Architecture:** 동작은 바꾸지 않는다. 문서 문장과 주석만 고친다. 각 Task는 고친 문장이 코드와 맞는지 grep·읽기 명령으로 확인한다.

**Tech Stack:** Markdown, Java 주석, YAML 주석. (코드 줄 번호는 2026-11-27 main 기준이라 시작 전에 다시 확인한다.)

**Spec:** 없음. 근거는 루트 `CLAUDE.md`의 "확정 사항"과 "미결 사항", `docs/deploy.md`, 해당 코드.

## Global Constraints

- 문서에 경과(날짜·PR 번호)를 쓰지 않는다. 위키 교훈 한 건만 예외이고 이 계획은 위키를 건드리지 않는다.
- 같은 내용을 두 곳에 쓰지 않는다. 덧붙이기 전에 고칠 문단이 있는지 먼저 본다.
- 브랜치 `docs/<범위>`(예: `docs/stale-docs-refresh`), 커밋 `docs: 한국어 설명`. 커밋은 요청이 있을 때만 한다.
- 코드는 주석만 바꾼다. 식별자·동작 변경 금지. 작업 뒤 `cd backend && ./gradlew compileJava`가 통과해야 한다.
- 진짜 미결은 남긴다: 원본 보관 기간·자동 삭제, 자료 형식(HWP·HWPX), AI 요약 영상, 사용자 테스트 뒤 조정할 값, 개인정보처리방침 시행일 등.
- **순서:** 숙련도 계산 계획(`2026-11-27-mastery-per-question.md`)이 `docs/frontend-requests.md` 5-10절을 고친다. 이 PR은 그 뒤에 하거나, 5-10절과 `MasteryCalculator` 관련 문장은 건드리지 않는다. `LoginAttemptLimiter.java`·`SignupAttemptLimiter.java`·`AuthController.java:66`의 프록시·IP 주석은 로그인 제한 계획에서 고치므로 제외한다.

## Review Focus

- 자료 상세 `concepts` 설명이 프론트 `MaterialConcept`(`frontend/src/types/material.ts`)와 백엔드 `ConceptResponse`와 모두 같은지.
- `studyNote` 설명을 618줄 근처 한 곳에만 두고, 195줄 근처는 그 절을 가리키기만 하는지(같은 내용 두 곳 금지).
- S3를 "쓰지 않는다"로 고쳐도 `FileStorage` 인터페이스가 구현 추가를 허용한다는 설명과 `backend/CLAUDE.md`의 구조 설명(S3는 구현 추가)이 어긋나지 않는지.
- 원본 보관 기간 TODO를 지우지 않는지(진짜 미결).

## File Structure

- `docs/frontend-requests.md` — 자료 상세 타입 블록(191·195줄 근처), 인증 표(219줄 근처)
- `docs/backend-design.md` — 14·252·355줄의 S3 문장
- `backend/src/main/java/com/msg/backend/global/storage/FileStorage.java` — 9줄 주석
- `backend/src/main/resources/application.yaml` — 43줄 주석

### Task 1: 자료 요약·상세 타입 블록 고치기

**Files:**
- Modify: `docs/frontend-requests.md:191-196`

**Interfaces:**
- Consumes: 실제 타입 `ConceptResponse(conceptId, name, aiDescription, evidenceLocation, evidenceQuote, courseKeyword, studyNote)` (`MaterialDetailResponse.java:37`), `MaterialSummaryResponse.quizCount`(퀴즈 표에서 센 값).
- Produces: 없음.

- [ ] **Step 1: 현재 값 확인**

Run: `sed -n 185,198p docs/frontend-requests.md; sed -n 1,40p frontend/src/types/material.ts`
Expected: `quizCount` 주석이 "퀴즈 기능 전이라 지금은 0", `concepts`가 3개 필드. 다르면 줄 번호를 고치고 이미 맞는 항목은 뺀다.

- [ ] **Step 2: `quizCount` 주석을 "이 자료에 연결된 내 퀴즈 수"로 바꾼다**

`MaterialSummaryResponse.java:17`의 `@Schema` 설명과 같은 뜻으로 쓴다.

- [ ] **Step 3: `concepts` 항목을 7개 필드로 바꾼다**

`conceptId, name, aiDescription, evidenceLocation(쪽·슬라이드, 없으면 null), evidenceQuote(자료 원문 근거 문장), courseKeyword(boolean), studyNote`. `studyNote`는 형식을 다시 쓰지 않고 "5-9 `study-notes` 절 참고" 한 줄로 가리킨다. 프론트 `MaterialConcept`의 필드 이름과 순서를 그대로 따른다.

- [ ] **Step 4: 검증**

Run: `grep -n "evidenceQuote\|courseKeyword" docs/frontend-requests.md frontend/src/types/material.ts backend/src/main/java/com/msg/backend/domain/material/dto/MaterialDetailResponse.java; grep -c "studyNote" docs/frontend-requests.md`
Expected: 세 파일 모두에서 필드 이름이 같다. `studyNote`의 형식 설명은 618줄 근처에만 있다.

### Task 2: 탈퇴 요청 표를 본문과 맞추기

**Files:**
- Modify: `docs/frontend-requests.md:219`

- [ ] **Step 1: 확인**

Run: `sed -n 219p docs/frontend-requests.md; grep -n "confirmText" docs/frontend-requests.md backend/src/main/java/com/msg/backend/domain/user/api/UserController.java frontend/src/api/auth.api.ts`
Expected: 표는 `{ password }`, 본문(249줄 근처)은 `{ password?, confirmText? }`.

- [ ] **Step 2: 표의 요청 본문 칸을 `{ password?, confirmText? }`로 바꾼다**

규칙 설명(비밀번호가 있는 계정은 `password`, 카카오 전용 계정은 `confirmText: "탈퇴"`)은 본문에 이미 있으므로 표에는 쓰지 않는다. 표 칸 너비 정렬을 유지한다.

- [ ] **Step 3: 검증**

Run: `grep -n "{ password" docs/frontend-requests.md`
Expected: 남은 곳이 `{ password?, confirmText? }`뿐이고, 마이페이지 절의 "비밀번호를 다시 넣는 탈퇴" 같은 서술형 문장은 본문과 모순되지 않는지 읽어서 확인한다.

### Task 3: 이미 확정된 AWS 저장소 구성의 낡은 주석 고치기

**Files:**
- Modify: `backend/src/main/java/com/msg/backend/global/storage/FileStorage.java:9`
- Modify: `backend/src/main/resources/application.yaml:43`
- Modify: `docs/backend-design.md:14,252,355`

**Interfaces:**
- Consumes: 확정 사항(루트 `CLAUDE.md`): EC2 한 대 + Docker Compose, RDS·S3는 쓰지 않고 도커 볼륨 `uploads`에 두고 매일 백업. `application-prod.yaml:7`의 `forward-headers-strategy: native`, `deploy/Caddyfile:19`.
- Produces: 없음.

- [ ] **Step 1: 확인**

Run: `grep -rn "TODO(미결)" backend/src/main | grep -i "aws\|s3\|프록시\|배포"; grep -n "S3" docs/backend-design.md`
Expected: 저장소(S3) 관련 줄이 나온다. `AuthController.java:66`처럼 프록시·IP 관련 줄은 로그인 제한 계획 몫이라 여기서 고치지 않는다(그 PR이 먼저 머지됐으면 이미 없다). 맞지 않는 줄은 빼고 보고한다.

- [ ] **Step 2: `FileStorage.java:9`의 `TODO(미결)` 줄을 지우고 뜻을 앞 문장에 합친다**

"운영은 EC2의 도커 볼륨(`uploads`)에 `LocalFileStorage`로 두고 S3는 쓰지 않는다. 다른 저장소가 필요해지면 이 인터페이스의 구현을 추가하고 `app.storage.type`으로 고른다." 취지로 앞 문장(8줄 근처)과 겹치지 않게 한 문단으로 정리한다.

- [ ] **Step 3: `application.yaml:43` 주석에서 운영 저장소 부분만 뺀다**

"원본의 장기 보관 기간·자동 삭제 정책은 정해지지 않았다"만 `TODO(미결)`로 남기고 "AWS S3 등 운영 저장소는" 부분을 지운다. 운영 저장소는 도커 볼륨이라는 한 줄(`deploy/compose.yaml`의 `STORAGE_LOCAL_ROOT`)을 덧붙여도 된다.

- [ ] **Step 4: `docs/backend-design.md` 세 곳 고치기**

14줄 "S3는 미결" → "운영은 도커 볼륨, S3는 쓰지 않는다"(근거는 `docs/deploy.md`에 있으니 링크만). 252줄 "(로컬 / S3)" → "(로컬 디스크, 다른 저장소는 구현 추가)". 355줄 `TODO(미결)`는 "원본의 장기 보관 기간과 자동 삭제"만 남기고 "운영 저장소(AWS S3 등)"를 뺀다. 개인정보처리방침 4번을 같은 PR에서 고친다는 뒷문장은 그대로 둔다.

- [ ] **Step 5: 검증**

Run: `grep -rn "S3" backend/src/main docs/backend-design.md backend/CLAUDE.md; grep -rn "TODO(미결)" backend/src/main | grep -i "aws\|프록시"; cd backend && ./gradlew compileJava`
Expected: 남은 `S3`는 "쓰지 않는다"·"구현 추가" 문맥뿐이고, AWS·S3 `TODO(미결)`은 없다(프록시 주석은 로그인 제한 PR 몫). 컴파일 통과.

### Task 4: 남은 `TODO(미결)` 전수 대조 결과 반영

**Files:**
- Modify: 없음(대조 결과만 남기는 Task, 아래 표 밖에서 낡은 곳이 새로 나오면 해당 파일).

- [ ] **Step 1: 전수 목록 뽑기**

Run: `grep -rn "TODO(미결)" backend/src frontend/src docs deploy .github | grep -v node_modules`
Expected: 아래 판정과 같다. 새 줄이 있으면 루트 `CLAUDE.md` "미결 사항"과 대조해 판정을 추가한다.

판정(2026-11-27 기준):

| 위치 | 판정 |
| --- | --- |
| `application.yaml:43` 앞부분(보관 기간·자동 삭제) | 진짜 미결, 남김 |
| `application.yaml:55`, `:102` (200MB·100개, 500·40) | 사용자 테스트 뒤 조정할 값, 남김 |
| `AnalysisQueryService.java:205` (대화 신호 단위) | 숙련도 계산 계획이 다룸, 이 PR 제외 |
| `MaterialFileFormat.java:8`, `materials.ts:2` (HWP·HWPX) | 진짜 미결, 남김 |
| `sources/index.ts:6` (Gemini 가져오기) | 2단계 후보, 남김 |
| `MaterialDetailPage.tsx:187` (AI 요약 영상) | 진짜 미결, 남김 |
| `PrivacyPage.tsx:22`, `TermsPage.tsx:8` | 시행일·보호책임자 등 미결, 남김 |
| `backend-design.md:294`, `:313` | 대화 정리·모델 이름·비동기 여부 등 미결. 313줄 아래 "숙련도 점수 산식" 취소선 줄은 숙련도 계산 계획이 다루므로 제외 |
| `frontend-requests.md:38` | 미결 처리 규칙 설명, 남김 |
| `wiki/process.md:7` | 위키 교훈(기록), 건드리지 않음 |
| `FileStorage.java:9`, `application.yaml:43` 뒷부분, `backend-design.md:14,252,355` | Task 3에서 고침 |
| `LoginAttemptLimiter.java:19`, `SignupAttemptLimiter.java:20`, `AuthController.java:66` | 로그인 제한 계획에서 고침, 제외 |

DB·AWS·산식 초기값을 미결로 적은 곳은 위 Task 3의 항목 외에는 찾지 못했다(`docs/handover.md:106`의 S3 서술은 당시 대화 기록이라 그대로 둔다).

- [ ] **Step 2: 최종 검증**

Run: `git diff --stat; cd backend && ./gradlew compileJava`
Expected: 변경 파일이 이 계획의 File Structure 안에만 있다. 컴파일 통과.

## Self-Review

- 커버리지: 낡은 곳 5건(자료 상세 타입, quizCount, 탈퇴 표, AWS·프록시 주석, 미결 전수)이 Task 1~4에 모두 있다.
- 일관성: `studyNote` 설명은 한 곳, 보관 기간 TODO는 `application.yaml`·`backend-design.md` 두 곳에 있지만 한쪽은 설정 주석이고 다른 쪽은 설계 문서라 역할이 다르다(같은 뜻을 반복하지 않도록 yaml은 한 줄로).
- 비율: 문서·주석만 고치는 계획이라 코드 블록을 쓰지 않았다.
