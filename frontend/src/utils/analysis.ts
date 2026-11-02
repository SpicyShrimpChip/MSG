import type {
  AnalysisProvider,
  AnalysisRun,
  ConceptComparison,
  ConceptResult,
  UnderstandingStatus,
} from '@/types';

export const PROVIDER_LABEL: Record<AnalysisProvider, string> = {
  gpt: 'GPT',
  claude: 'Claude',
  gemini: 'Gemini',
};

export const UNDERSTANDING_LABEL: Record<UnderstandingStatus, string> = {
  understood: '이해',
  partial: '부분 이해',
  not_understood: '이해 부족',
  insufficient_evidence: '판단 근거 부족',
};

// 모델들의 판단을 정확도가 아니라 "몇 개 모델이 같은 판단을 했는지"로 읽는다
export function describeComparison(comparison: ConceptComparison): string {
  const { outcome, judgedCount, agreeCount } = comparison;
  switch (outcome) {
    case 'unanimous':
      return `${judgedCount}개 모두 같음`;
    case 'majority':
      return `${judgedCount}개 중 ${agreeCount}개 같음`;
    case 'disagreement':
      return `${judgedCount}개가 서로 다름`;
    case 'single_model':
      return '1개만 판단 · 비교 못 함';
    case 'no_result':
      return '판단한 모델 없음';
  }
}

// 이번 분석에서 실제로 판단한 모델 수 / 전체 모델 수
export function countJudgingModels(run: AnalysisRun) {
  return {
    judged: run.models.filter((model) => model.status === 'succeeded').length,
    total: run.models.length,
  };
}

// 추천 학습 순서대로 (서버가 이미 정렬해 주지만 순서를 믿고 그리지 않는다)
export function sortByStudyOrder(concepts: ConceptResult[]): ConceptResult[] {
  return [...concepts].sort((a, b) => a.studyOrder - b.studyOrder);
}
