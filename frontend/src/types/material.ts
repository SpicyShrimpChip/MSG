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

// 자료 상세 (GET /api/materials/{id}). ai로 시작하는 필드는 AI가 쓴 글이라 화면에서 보라색 "AI" 표시를 붙인다
export interface MaterialDetail extends MaterialSummary {
  aiSummary: string | null;
  concepts: MaterialConcept[];
}
