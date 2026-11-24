// 근거 기반 학습 표시 도우미: 인지 수준(개정 블룸), 원문 근거 검증, 학습 행동, 검증 상태.
// 값은 모두 서버가 정하고, 여기서는 사용자에게 보여 줄 글자만 정한다 (정확도·완벽 같은 말은 쓰지 않는다)
import type {
  BloomLevel,
  LearningBehavior,
  QuizMode,
  QuizResult,
  SourceCheck,
  VerificationStatus,
} from '@/types';

// 낮은 수준부터 (백엔드 BloomLevel 순서와 같다)
export const BLOOM_LEVELS: {
  value: BloomLevel;
  label: string;
  description: string;
}[] = [
  { value: 'remember', label: '기억', description: '정의·사실을 떠올려요' },
  {
    value: 'understand',
    label: '이해',
    description: '자기 말로 설명하고 비교해요',
  },
  { value: 'apply', label: '적용', description: '주어진 상황에 개념을 써요' },
  {
    value: 'analyze',
    label: '분석',
    description: '구성 요소·원인·관계를 나눠 봐요',
  },
  {
    value: 'evaluate',
    label: '평가',
    description: '기준을 세워 대안을 판단해요',
  },
  {
    value: 'create',
    label: '창조',
    description: '요구사항에 맞는 구성을 설계해요 (서술형만)',
  },
];

export const BLOOM_LABEL = Object.fromEntries(
  BLOOM_LEVELS.map((level) => [level.value, level.label]),
) as Record<BloomLevel, string>;

// 학습 목적마다 평가하려는 기본 수준 (백엔드 BloomPlan.defaults와 같다)
export const MODE_DEFAULT_BLOOM: Record<QuizMode, BloomLevel[]> = {
  exam_prep: ['remember', 'understand', 'apply', 'analyze'],
  self_development: ['understand', 'apply', 'analyze', 'evaluate'],
  work: ['apply', 'analyze', 'evaluate', 'create'],
};

// 고른 수준을 낮은 수준부터 정렬한다. 서술형이 없으면 창조를 뺀다 (서버 규칙과 같다)
export function effectiveBloomLevels(
  chosen: BloomLevel[],
  hasEssay: boolean,
): BloomLevel[] {
  return BLOOM_LEVELS.map((level) => level.value).filter(
    (value) => chosen.includes(value) && (hasEssay || value !== 'create'),
  );
}

export const SOURCE_CHECK_LABEL: Record<SourceCheck, string> = {
  verified: '원문과 일치',
  partial: '일부 일치',
  failed: '원문에서 찾지 못함',
  insufficient: '인용 없음',
};

export const VERIFICATION_LABEL: Record<VerificationStatus, string> = {
  verified: '검증 완료',
  partial: '일부 검증',
  needs_check: '추가 확인 필요',
  insufficient: '근거 부족',
  failed: '검증 실패',
};

export const BEHAVIOR_LABEL: Record<LearningBehavior, string> = {
  confusion_expressed: '이해 부족 표현',
  re_explanation_request: '재설명 요청',
  deepening_question: '심화 질문',
  incorrect_explanation: '잘못된 자기 설명',
  correct_explanation: '정확한 자기 설명',
  application: '새 상황에 적용',
  no_signal: '판단 근거 없음',
};

// 행동이 판단에 어떻게 쓰이는지 (서버 LearningBehavior.Signal과 같다)
export function behaviorSignal(
  behavior: LearningBehavior,
): 'positive' | 'negative' | 'neutral' {
  if (behavior === 'correct_explanation' || behavior === 'application')
    return 'positive';
  if (
    behavior === 'confusion_expressed' ||
    behavior === 'incorrect_explanation'
  )
    return 'negative';
  return 'neutral';
}

// 원문 근거 검증 상태를 공통 검증 상태 색으로 (원문 일치 → 검증 완료 색 등)
export function sourceCheckTone(check: SourceCheck): VerificationStatus {
  return check === 'insufficient' ? 'insufficient' : check;
}

// 퀴즈 결과 위쪽 한 줄: 자료 근거를 몇 문제에서 확인했는지 (자료 본문을 쓰지 않았으면 null)
export function describeGrounding(
  design: QuizResult['learningDesign'],
): string | null {
  if (!design?.materialContentUsed) return null;
  const { verified, partial, failed, insufficient } = design.grounding;
  const total = verified + partial + failed + insufficient;
  if (total === 0) return null;
  const parts = [`원문과 일치 ${verified}문제`];
  if (partial > 0) parts.push(`일부 일치 ${partial}문제`);
  if (failed + insufficient > 0)
    parts.push(`근거 확인 안 됨 ${failed + insufficient}문제`);
  return `${total}문제 중 ${parts.join(', ')}`;
}
