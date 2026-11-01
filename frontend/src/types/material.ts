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

// 자료에서 뽑은 핵심 개념. conceptId는 학습 대시보드 ConceptMastery.conceptId와 같은 값
export interface MaterialConcept {
  conceptId: string;
  name: string;
  // 이 자료에서 그 개념을 어떻게 다루는지 (AI가 쓴 설명)
  aiDescription: string | null;
}

// 자료 상세 (GET /api/materials/{id}). ai로 시작하는 필드는 AI가 쓴 글이라 화면에서 보라색 "AI" 표시를 붙인다
export interface MaterialDetail extends MaterialSummary {
  aiSummary: string | null;
  concepts: MaterialConcept[];
}
