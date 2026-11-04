import type {
  AssessmentComparison,
  AssessmentConceptDelta,
  AssessmentSnapshot,
  ConceptDeltaStatus,
} from '@/types';

// 0~100 반올림 (만점이 0이거나 값이 없으면 null: 채점이 끝난 답이 없다)
export function scorePercent(
  score: number | null,
  max: number | null,
): number | null {
  if (score === null || max === null || max === 0) return null;
  return Math.round((score / max) * 100);
}

export const submitted = (snapshot: AssessmentSnapshot | null) =>
  snapshot !== null && snapshot.submissionId !== null;

export interface ComparisonRow {
  key: string;
  label: string;
  pre: number | null;
  post: number | null;
  preText: string;
  postText: string;
  // 사후 - 사전 (%p, 서버 계산). 둘 중 하나라도 채점된 답이 없으면 null
  delta: number | null;
  status: ConceptDeltaStatus | null;
}

const scoreText = (score: number | null, max: number | null) =>
  score === null || max === null ? '미응시' : `${score}/${max}점`;

// 비교 그래프의 줄: 맨 위 전체, 그 아래 개념별. 안 푼 평가는 값 없이 "미응시"
export function comparisonRows(data: AssessmentComparison): ComparisonRow[] {
  const pre = submitted(data.pre) ? data.pre : null;
  const post = submitted(data.post) ? data.post : null;
  return [
    {
      key: 'overall',
      label: '전체',
      pre: pre ? scorePercent(pre.score, pre.maxScore) : null,
      post: post ? scorePercent(post.score, post.maxScore) : null,
      preText: pre ? scoreText(pre.score, pre.maxScore) : '미응시',
      postText: post ? scoreText(post.score, post.maxScore) : '미응시',
      delta: data.overallDelta,
      status: null,
    },
    ...data.concepts.map((concept) => ({
      key: concept.conceptId,
      label: concept.name,
      pre: scorePercent(concept.preScore, concept.preMaxScore),
      post: scorePercent(concept.postScore, concept.postMaxScore),
      preText: scoreText(concept.preScore, concept.preMaxScore),
      postText: scoreText(concept.postScore, concept.postMaxScore),
      delta: concept.delta,
      status: concept.status,
    })),
  ];
}

// %p 변화를 글로. 양수에 +를 붙인다
export const deltaText = (delta: number | null) =>
  delta === null ? '' : delta > 0 ? `+${delta}%p` : `${delta}%p`;

export interface ConceptGroups {
  improved: AssessmentConceptDelta[];
  // 사후평가에서도 기준 미만이거나 떨어진 개념 (사후 전이면 사전 기준 미만)
  needsReview: AssessmentConceptDelta[];
}

// 상태는 서버 규칙(AssessmentService.status) 그대로 나눈다. 화면에서 다시 판단하지 않는다
export function groupConcepts(data: AssessmentComparison): ConceptGroups {
  return {
    improved: data.concepts.filter((c) => c.status === 'improved'),
    needsReview: data.concepts.filter(
      (c) =>
        c.status === 'still_weak' ||
        c.status === 'declined' ||
        c.status === 'weak',
    ),
  };
}
