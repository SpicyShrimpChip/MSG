// 맞춤 학습 순서 (GET /api/curriculum/courses/{courseId}). 백엔드 CurriculumResponse와 같은 모양.
// 순서·단계·이유(reasons)는 서버 코드 규칙(파랑)이고, aiSuggestions만 AI가 퀴즈를 만들 때 제안한 개념(보라)이다
import type { QuizMode } from './quiz';
import type { ConceptStudy } from './study';

export type CurriculumStage =
  | 'priority_study'
  | 'review'
  | 'analysis_weak'
  | 'needs_check'
  | 'not_assessed'
  | 'ready';

export interface CurriculumStep {
  order: number;
  conceptId: string;
  concept: string;
  stage: CurriculumStage;
  // 이 순서인 이유 (코드가 센 근거 문장)
  reasons: string[];
  evidence: {
    questionCount: number;
    gradedCount: number;
    correctCount: number;
    score: number;
    maxScore: number;
    pendingCount: number;
    reviewCount: number;
    analysisStatus: string | null;
    analysisOutcome: string | null;
    feedback: 'already_know' | 'want_to_study' | null;
    confirmation: string | null;
  };
  actions: {
    type:
      | 'review_quiz'
      | 'confirm_quiz'
      | 'first_quiz'
      | 'study_material'
      | 'next_step';
    label: string;
    quizMode: QuizMode | null;
  }[];
}

export interface Curriculum {
  courseId: number;
  courseName: string;
  learningPurpose: string;
  recommendedMode: QuizMode;
  steps: CurriculumStep[];
  // 이 과목에 올린 내 자료 (본문은 읽지 않았다)
  materials: { id: number; title: string }[];
  // AI가 퀴즈를 만들 때 제안한 개념 (실제 학습 결과가 아님)
  aiSuggestions: {
    kind: 'next' | 'prerequisite' | 'related';
    concept: string;
    aiReason: string | null;
  }[];
  // 4단계 학습 흐름 (서버 코드 규칙, AI 아님)
  flow: CurriculumFlow;
}

export type FlowStepKey =
  'pre_test' | 'concept_study' | 'concept_quiz' | 'post_test';

// 사전 테스트 → 핵심 개념 학습 → 개념별 확인 퀴즈 → 사후 평가. 단계 상태·지금 단계는 제출·채점 기록으로 서버가 정한다.
// 개념 학습(설명 읽기)은 기록하지 않아서, 확인 퀴즈를 시작하면 개념 학습을 마친 것으로 본다
export interface CurriculumFlow {
  // 지금 하면 좋은 단계 (모두 끝났으면 done). 사용자는 어느 단계로든 갈 수 있다
  currentStep: FlowStepKey | 'done';
  stages: {
    key: FlowStepKey;
    order: number;
    title: string;
    status: 'done' | 'current' | 'upcoming';
    // 이 단계의 지금 상태 (코드가 센 사실)
    summary: string;
  }[];
  // material: 자료에서 뽑은 개념 / course: 과목 키워드
  conceptSource: 'material' | 'course';
  // 사전·사후 평가에 쓸 자료 (없으면 null → 자료부터 올린다)
  assessmentMaterial: { id: number; title: string } | null;
  preTest: FlowAssessment;
  concepts: FlowConcept[];
  postTest: FlowAssessment;
  // 전체 향상 폭 (%p). 둘 중 하나라도 채점 결과가 없으면 null
  overallDelta: number | null;
  // 개념별 학습 진행 (학습 완료 기록·확인 퀴즈 결과로 센 값)
  progress: StudyProgress;
}

// 학습 진행률: 분모는 학습 기록을 남길 수 있는 개념(자료에서 뽑은 개념), 분자는 "학습 완료"를 누른 개념
export interface StudyProgress {
  totalConcepts: number;
  studiableConcepts: number;
  studiedConcepts: number;
  // 소수 첫째 자리 %. 학습할 수 있는 개념이 없으면 null
  percent: number | null;
  checkedConcepts: number;
  understoodConcepts: number;
  reviewNeededConcepts: number;
  needsCheckConcepts: number;
  lastStudiedAt: string | null;
  lastStudiedConcept: string | null;
}

export interface FlowAssessment {
  // none: 아직 만들지 않음 / not_submitted: 만들었지만 안 풂 / submitted: 제출함
  status: 'none' | 'not_submitted' | 'submitted';
  quizId: number | null;
  score: number | null;
  maxScore: number | null;
  rate: number | null;
  pendingCount: number;
  submittedAt: string | null;
}

export interface FlowConcept {
  conceptId: string;
  name: string;
  // 이 개념을 공부할 자료 (자료 상세 "핵심 개념" 탭으로 간다). 없으면 null
  materialId: number | null;
  materialTitle: string | null;
  // 그 자료에 자세한 설명이 있는지
  hasStudyNote: boolean;
  preRate: number | null;
  preStatus: 'weak' | 'ok' | 'not_taken';
  practiceGradedCount: number;
  practiceScore: number;
  practiceMaxScore: number;
  practiceStatus: 'not_started' | 'pending' | 'weak' | 'ok';
  postRate: number | null;
  delta: number | null;
  // 사후 평가 뒤 비교 상태 (AssessmentComparison의 status와 같음). 사후 평가 전이면 null
  postStatus: string | null;
  // 사전 테스트에서 부족했거나 채점 결과가 없어 먼저 공부하면 좋은 개념
  priority: boolean;
  // 자료에서 뽑은 개념의 학습 상태. 과목 키워드뿐인 개념은 null
  study: ConceptStudy | null;
}
