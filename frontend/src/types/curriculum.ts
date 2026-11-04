// 맞춤 학습 순서 (GET /api/curriculum/courses/{courseId}). 백엔드 CurriculumResponse와 같은 모양.
// 순서·단계·이유(reasons)는 서버 코드 규칙(파랑)이고, aiSuggestions만 AI가 퀴즈를 만들 때 제안한 개념(보라)이다
import type { QuizMode } from './quiz';

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
}
