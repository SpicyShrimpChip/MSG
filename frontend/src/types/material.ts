import type { SourceCheck } from './quiz';

// 자료 올리기 응답 (POST /api/materials). courseId: 과목 없이 올린 자료는 null
export interface Material {
  id: number;
  courseId: number | null;
  title: string;
}

// 자료 올리기 요청. 파일이 있어 multipart/form-data로 보낸다
export interface MaterialUploadInput {
  courseId: number;
  title: string;
  file: File;
  // 화면이 올리기마다 만든 요청 키(UUID, 64자까지). 같은 키로 다시 보내면 서버가 새로 저장하지 않고 이미 올린 자료를 돌려준다
  requestKey: string;
}

// uploaded: 파일 저장 완료, AI 분석 전 / processing: 글 뽑기·AI 분석 중 / ready: 완료 / failed: 글을 뽑지 못함 등
export type MaterialStatus = 'uploaded' | 'processing' | 'ready' | 'failed';

// 내 학습자료 목록 한 줄 (GET /api/materials)
export interface MaterialSummary {
  id: number;
  // 과목 없이 올린 자료는 null (과목을 지우면 그 과목의 자료도 함께 지워진다)
  courseId: number | null;
  title: string;
  fileName: string;
  createdAt: string; // ISO 문자열
  status: MaterialStatus;
  quizCount: number;
}

// 자료에서 뽑은 핵심 개념. conceptId는 퀴즈 개념(QuizConcept.conceptId)과 같은 규칙의 값
export interface MaterialConcept {
  conceptId: string;
  name: string;
  // 이 자료에서 그 개념을 어떻게 다루는지 (AI가 쓴 설명)
  aiDescription: string | null;
  // 근거: 서버가 자료 본문에 실제로 있는지 확인한 문장과 위치("p. 3", "슬라이드 2"). 위치를 모르면 null
  evidenceLocation: string | null;
  evidenceQuote: string;
  // 과목 키워드와 같은 개념인지 (자료 개념과 과목 키워드를 구분해 보여 준다)
  courseKeyword: boolean;
  // 개념 공부용 자세한 설명 (AI). 아직 만들지 않았으면 null
  studyNote: ConceptStudyNote | null;
}

// 개념 공부용 설명 (백엔드 ConceptStudyNoteResponse, V11). contextQuote·contextLocation만 서버가 자료 본문에서
// 확인한 원문이고(회색 인용), 나머지 글은 AI가 자료 발췌를 근거로 쓴 것이다(보라)
export interface ConceptStudyNote {
  explanation: string;
  // 자료 안에서 어떤 내용과 이어지는지
  materialContext: string | null;
  contextQuote: string | null;
  contextLocation: string | null;
  // 왜 중요한지
  importance: string | null;
  example: string | null;
  // 설명을 쓴 모델 (gpt / claude / gemini)
  generatedBy: string;
  createdAt: string;
}

// 핵심 개념 뽑기 (POST /api/materials/{id}/concepts). 자료 본문 발췌를 외부 AI로 보내므로 이번 요청의 확인이 필요하다
export interface ConceptExtractInput {
  materialContentConsent: boolean;
}

// 개념 하나의 공부용 설명 만들기 (POST /api/materials/{id}/concepts/study-notes). 자료 본문 발췌를 외부 AI로 보낸다
export interface ConceptStudyNoteInput {
  conceptId: string;
  materialContentConsent: boolean;
}

// 요약본의 학습 목적 (퀴즈 학습 모드와 같은 값)
export type StudySummaryPurpose = 'exam_prep' | 'self_development' | 'work';

// 요약 항목 하나. text·name은 AI가 쓴 글, quote·location·check는 서버가 원문에서 확인한 값
export interface StudySummaryItem {
  name: string | null;
  text: string;
  // material: 원문에 근거 / supplement: 자료에 없는 AI 보충 설명 (검증 대상 아님)
  basis: 'material' | 'supplement';
  quote: string | null;
  location: string | null;
  check: SourceCheck | null;
}

// 자료 요약본 (GET·POST /api/materials/{id}/summary, 백엔드 StudySummaryResponse)
export interface StudySummary {
  materialId: number;
  purpose: StudySummaryPurpose;
  status: 'none' | 'running' | 'ready';
  // 저장한 요약을 다시 썼는지 (모델을 부르지 않음)
  reused: boolean;
  // 자료 파일이나 요약 방식이 바뀌어 다시 만들어야 하는지
  stale: boolean;
  staleReason: string | null;
  generatedBy: { provider: string; model: string | null } | null;
  promptVersion: string | null;
  createdAt: string | null;
  verification: {
    status: 'verified' | 'partial' | 'needs_check' | 'insufficient';
    verified: number;
    partial: number;
    failed: number;
    insufficient: number;
    supplement: number;
  } | null;
  sourceScope: string[];
  sourceTruncated: boolean;
  content: {
    overview: StudySummaryItem;
    concepts: StudySummaryItem[];
    principles: StudySummaryItem[];
    relations: StudySummaryItem[];
    examples: StudySummaryItem[];
    confusions: StudySummaryItem[];
    keyPoints: StudySummaryItem[];
    selfCheck: { question: string; hint: string | null }[];
  } | null;
  sources: { location: string; count: number }[];
}

// 요약본 만들기 (POST /api/materials/{id}/summary). 자료 본문을 외부 AI로 보내므로 이번 요청의 확인이 필요하다
export interface StudySummaryInput {
  purpose: StudySummaryPurpose;
  materialContentConsent: boolean;
  regenerate?: boolean;
}

// 자료 상세 (GET /api/materials/{id}). ai로 시작하는 필드는 AI가 쓴 글이라 화면에서 보라색 "AI" 표시를 붙인다
export interface MaterialDetail extends MaterialSummary {
  aiSummary: string | null;
  concepts: MaterialConcept[];
}
