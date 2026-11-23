import type {
  AnalysisProvider,
  AnalysisRun,
  ConceptComparison,
  ConceptFeedbackType,
  ConceptResult,
  MasteryStage,
  UnderstandingStatus,
} from '@/types';

export const PROVIDER_LABEL: Record<AnalysisProvider, string> = {
  gpt: 'GPT',
  claude: 'Claude',
  gemini: 'Gemini',
};

// 숙련도 단계 (코드 계산). 근거 부족은 이해 부족과 다르다
export const MASTERY_LABEL: Record<MasteryStage, string> = {
  insufficient_evidence: '근거 부족',
  needs_work: '보완 필요',
  normal: '보통',
  strong: '강점',
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

export const FEEDBACK_LABEL: Record<ConceptFeedbackType, string> = {
  already_know: '이미 알아요',
  want_to_study: '더 공부할래요',
};

// 대시보드 맨 위 "한눈에 보기"에 쓰는 개수 (코드가 센 값).
// 숙련도가 없는 개념(아직 계산 전)은 근거 부족으로 센다. 모델 의견은 정확도가 아니라 같은 판단을 한 모델 수로 나눈다
export interface ConceptOverview {
  total: number;
  mastery: Record<MasteryStage, number>;
  comparison: {
    unanimous: number;
    majority: number;
    disagreement: number;
    // 모델 1개만 판단했거나 판단이 없어 비교하지 못한 개념
    notCompared: number;
  };
}

export function summarizeConcepts(concepts: ConceptResult[]): ConceptOverview {
  const overview: ConceptOverview = {
    total: concepts.length,
    mastery: { strong: 0, normal: 0, needs_work: 0, insufficient_evidence: 0 },
    comparison: { unanimous: 0, majority: 0, disagreement: 0, notCompared: 0 },
  };
  for (const concept of concepts) {
    overview.mastery[concept.mastery?.level ?? 'insufficient_evidence'] += 1;
    const outcome = concept.comparison.outcome;
    if (outcome === 'single_model' || outcome === 'no_result') {
      overview.comparison.notCompared += 1;
    } else {
      overview.comparison[outcome] += 1;
    }
  }
  return overview;
}
