import type { ConceptQuizStats } from './quiz';
// 학습 분석 (여러 AI 모델 비교). 백엔드 /api/analysis 응답과 같은 모양 (docs/frontend-requests.md "학습 분석")
// 상태 비교·개수·순서는 코드가 정한 값, ai로 시작하는 필드만 AI가 쓴 글이다
// (화면에서 AI 글은 보라색 + "AI" 표시로 구분하므로 필드를 섞지 않는다)

// 강점·보완 필요 (퀴즈 결과 등에서 쓴다)
export type MasteryLevel = 'strength' | 'normal' | 'weakness';

export type AnalysisProvider = 'gpt' | 'claude' | 'gemini';

// 모델별 설정 여부 (키는 서버가 주지 않는다)
export interface ProviderStatus {
  provider: AnalysisProvider;
  model: string | null;
  configured: boolean;
}

// 이해·부분 이해·이해 부족·판단 근거 부족. 근거 부족은 이해 부족과 다르다
export type UnderstandingStatus =
  'understood' | 'partial' | 'not_understood' | 'insufficient_evidence';

export interface JudgmentEvidence {
  messageId: number;
  conversationId: number;
  conversationTitle: string;
  role: 'user' | 'assistant';
  kind:
    'user_explanation' | 'user_solution' | 'user_question' | 'ai_explanation';
  aiNote: string | null;
  // 메시지 앞 120자
  excerpt: string;
}

// 모델 하나의 판단
export interface ConceptJudgment {
  provider: AnalysisProvider;
  model: string | null;
  // 서버가 근거를 확인한 뒤의 상태
  status: UnderstandingStatus;
  // 모델이 처음 낸 상태. status와 다르면 adjustedReason이 있다
  reportedStatus: UnderstandingStatus;
  adjustedReason: string | null;
  aiReason: string | null;
  aiNeedsCheck: string | null;
  aiRecommendedActivity: string | null;
  evidence: JudgmentEvidence[];
}

// 판단한 모델 모두 같음 / 3개 중 2개 같음 / 모두 다름 / 하나만 판단 / 판단한 모델 없음
export type ComparisonOutcome =
  'unanimous' | 'majority' | 'disagreement' | 'single_model' | 'no_result';

export interface ConceptComparison {
  outcome: ComparisonOutcome;
  // 다수 의견. 모두 다르거나 판단이 없으면 null
  status: UnderstandingStatus | null;
  judgedCount: number;
  agreeCount: number;
  calledCount: number;
}

export type ConceptFeedbackType = 'already_know' | 'want_to_study';

export interface ConceptFeedback {
  id: number;
  courseId: number;
  concept: string;
  feedback: ConceptFeedbackType;
  comment: string | null;
  analysisRunId: number | null;
  updatedAt: string;
  nextAction: 'confirm_quiz' | 'prioritize_study';
}

// 피드백 저장 요청 본문. comment는 200자까지
export interface ConceptFeedbackInput {
  concept: string;
  feedback: ConceptFeedbackType;
  comment?: string;
  analysisRunId?: number;
}

export interface ConceptResult {
  // 개념 = 과목 키워드
  concept: string;
  comparison: ConceptComparison;
  judgments: ConceptJudgment[];
  feedback: ConceptFeedback | null;
  // 추천 학습 순서 (코드 규칙)
  studyOrder: number;
  recommendConfirmQuiz: boolean;
  // 이 개념의 퀴즈 결과 (코드 집계, AI 판단과 따로). 퀴즈 기록이 없으면 null
  quiz?: ConceptQuizStats | null;
  // 숙련도 단계와 근거 (코드 계산, AI 판단과 따로). 화면은 단계와 근거만 보여 주고 숫자 점수는 보여 주지 않는다
  mastery?: ConceptMastery | null;
}

// 근거 부족(insufficient_evidence)은 이해 부족과 다르다
export type MasteryStage =
  'insufficient_evidence' | 'needs_work' | 'normal' | 'strong';

export interface ConceptMastery {
  level: MasteryStage;
  // 0~1, 근거 부족이면 null. 화면에 숫자로 보여 주지 않는다
  score: number | null;
  evidenceWeight: number;
  quizGradedCount: number;
  quizRate: number | null;
  conversationCount: number;
  conversationStatus: 'understood' | 'partial' | 'not_understood' | null;
  formulaVersion: string;
  // 근거 부족일 때 단계가 나오려면 더 풀어야 하는 퀴즈 문제 수. 근거가 충분하면 null
  quizNeeded: number | null;
}

export type ModelRunStatus =
  'succeeded' | 'failed' | 'invalid_output' | 'not_configured';

export interface ModelRun {
  provider: AnalysisProvider;
  model: string | null;
  status: ModelRunStatus;
  // 실패·미설정 이유
  message: string | null;
  judgedConceptCount: number;
  droppedEvidenceCount: number;
}

export interface AnalysisRun {
  id: number;
  courseId: number;
  status: 'running' | 'completed' | 'partial' | 'failed';
  promptVersion: string;
  createdAt: string;
  completedAt: string | null;
  input: {
    conversationCount: number;
    messageCount: number;
    truncatedMessageCount: number;
  };
  models: ModelRun[];
  // studyOrder 순서로 온다
  concepts: ConceptResult[];
  // LLM이 만든 배치 JSON. 지금 백엔드는 주지 않는다(목업만). 믿지 않고 parseDashboardLayout으로 검증한 뒤에만 쓴다
  layout?: unknown;
}

// 대시보드 영역 id. 배치 JSON에는 이 값만 올 수 있다 (검증: utils/layout/dashboardLayout.ts)
export const DASHBOARD_COMPONENT_IDS = [
  'summary',
  'conceptTable',
  'conceptDetail',
  'models',
  'recommendation',
] as const;

export type DashboardComponentId = (typeof DASHBOARD_COMPONENT_IDS)[number];

export interface LayoutItem {
  component: DashboardComponentId;
  order: number;
  // wide: 한 줄 전체, normal: 넓은 화면에서 반 칸
  size: 'wide' | 'normal';
  // 이 위치·크기인 이유 (AI)
  aiReason: string | null;
}

// 배치를 만들 때 AI에게 준 숫자 (코드가 센 값, 이유와 함께 보여 준다)
export interface LayoutBasis {
  notUnderstoodCount: number;
  disagreementCount: number;
  conversationCount: number;
}

// 검증을 통과한 배치. 통과하지 못하면 기본 배치(explanation: null)를 쓴다
export interface DashboardLayout {
  items: LayoutItem[];
  // 전체 배치 이유(AI)와 그때 본 숫자(코드). 기본 배치면 없음
  explanation: { aiSummary: string; basis: LayoutBasis } | null;
}
