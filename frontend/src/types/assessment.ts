// 학습자료 기반 사전·사후평가 (백엔드 /api/assessments). 평가는 퀴즈 구조를 그대로 쓰고, 풀기·결과는 /quizzes/{id}
import type {
  AssessmentKind,
  QuestionType,
  QuizDifficulty,
  QuizMode,
} from './quiz';

export interface AssessmentCreateInput {
  requestKey: string;
  materialId: number;
  kind: AssessmentKind;
  // 사후평가는 서버가 사전평가와 같게 맞추므로 보내도 쓰지 않는다
  mode?: QuizMode;
  questionTypes?: QuestionType[];
  questionCount?: number;
  difficulty?: QuizDifficulty;
  // 자료 본문(발췌)을 외부 AI로 보내기와 이번 요청의 확인
  useMaterialContent?: boolean;
  materialContentConsent?: boolean;
}

export interface AssessmentSnapshot {
  quizId: number;
  title: string;
  // 아직 안 풀었으면 null
  submissionId: number | null;
  submittedAt: string | null;
  questionCount: number;
  gradedCount: number;
  // 채점이 끝난 답만 더한 점수 (서버 계산)
  score: number;
  maxScore: number;
  pendingCount: number;
  reviewCount: number;
}

// 최신 사전평가 vs 그 사후평가 (GET /api/assessments/comparison)
export interface AssessmentComparison {
  basis: string;
  material: { id: number; title: string } | null;
  pre: AssessmentSnapshot | null;
  post: AssessmentSnapshot | null;
  // 사후 정답률 - 사전 정답률 (%p, 서버 계산). 둘 다 채점된 답이 있을 때만
  overallDelta: number | null;
  concepts: AssessmentConceptDelta[];
  // 두 평가를 같은 조건으로 비교할 수 있는지 (서버 규칙). 사전평가가 없으면 null
  comparability: AssessmentComparability | null;
}

// improved/declined/unchanged: 둘 다 응시 / still_weak: 사후에도 기준 미만 / weak·ok: 사전만 / not_taken: 채점된 답 없음
export type ConceptDeltaStatus =
  | 'improved'
  | 'declined'
  | 'unchanged'
  | 'still_weak'
  | 'weak'
  | 'ok'
  | 'not_taken';

export interface AssessmentConceptDelta {
  conceptId: string;
  name: string;
  preScore: number | null;
  preMaxScore: number | null;
  postScore: number | null;
  postMaxScore: number | null;
  delta: number | null;
  status: ConceptDeltaStatus;
}

export interface AssessmentComparability {
  sameConcepts: boolean;
  sameQuestionCount: boolean;
  sameQuestionTypes: boolean;
  sameDifficulty: boolean;
  // 비교할 때 주의할 점 (서버가 쓴 문장, AI 글 아님)
  notes: string[];
}

// 퀴즈·평가에 쓸 AI (키 값 없음)
export interface QuizAiStatus {
  available: boolean;
  generation: { provider: string; model: string | null } | null;
  grading: { provider: string; model: string | null }[];
  message: string;
  // 학습자료 본문을 외부 AI로 보내는 기능이 켜져 있는지 (운영 설정, 기본 꺼짐)
  materialContentEnabled: boolean;
}
