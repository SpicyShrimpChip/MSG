import { describe, expect, it } from 'vitest';
import type { AssessmentComparison, AssessmentSnapshot } from '@/types';
import {
  comparisonRows,
  deltaText,
  groupConcepts,
  scorePercent,
} from './assessment';

const snapshot = (
  score: number,
  maxScore: number,
  submissionId: number | null = 1,
): AssessmentSnapshot => ({
  quizId: 1,
  title: '평가',
  submissionId,
  submittedAt: submissionId ? '2026-11-03T10:00:00' : null,
  questionCount: 6,
  gradedCount: 6,
  score,
  maxScore,
  pendingCount: 0,
  reviewCount: 0,
});

const data = (
  pre: AssessmentSnapshot | null,
  post: AssessmentSnapshot | null,
): AssessmentComparison => ({
  basis: '',
  material: null,
  pre,
  post,
  overallDelta: null,
  comparability: null,
  concepts: [
    {
      conceptId: 'nat',
      name: 'NAT',
      preScore: 1,
      preMaxScore: 3,
      postScore: null,
      postMaxScore: null,
      delta: null,
      status: 'weak',
    },
  ],
});

describe('사전·사후평가 비교', () => {
  it('만점이 0이거나 값이 없으면 퍼센트를 만들지 않는다', () => {
    expect(scorePercent(2, 3)).toBe(67);
    expect(scorePercent(0, 0)).toBeNull();
    expect(scorePercent(null, 3)).toBeNull();
  });

  it('전체 줄이 맨 위이고 사후평가를 안 봤으면 미응시', () => {
    const rows = comparisonRows(data(snapshot(3, 6), null));
    expect(rows[0]).toMatchObject({
      key: 'overall',
      pre: 50,
      post: null,
      postText: '미응시',
    });
    expect(rows[1]).toMatchObject({
      label: 'NAT',
      pre: 33,
      preText: '1/3점',
      post: null,
    });
  });

  it('변화량은 서버 값을 쓰고 개념은 서버 상태대로 나눈다', () => {
    const base = data(snapshot(3, 6), snapshot(5, 6));
    const concept = (
      conceptId: string,
      status: AssessmentComparison['concepts'][number]['status'],
      delta: number | null,
    ) => ({
      ...base.concepts[0],
      conceptId,
      name: conceptId,
      status,
      delta,
    });
    const groups = groupConcepts({
      ...base,
      overallDelta: 33,
      concepts: [
        concept('a', 'improved', 67),
        concept('b', 'still_weak', 33),
        concept('c', 'declined', -33),
        concept('d', 'unchanged', 0),
        concept('e', 'not_taken', null),
      ],
    });
    expect(groups.improved.map((c) => c.conceptId)).toEqual(['a']);
    expect(groups.needsReview.map((c) => c.conceptId)).toEqual(['b', 'c']);
    expect(comparisonRows({ ...base, overallDelta: 33 })[0].delta).toBe(33);
    expect(deltaText(33)).toBe('+33%p');
    expect(deltaText(-5)).toBe('-5%p');
    expect(deltaText(0)).toBe('0%p');
    expect(deltaText(null)).toBe('');
  });

  it('만들었지만 안 푼 평가는 점수로 보지 않는다', () => {
    const rows = comparisonRows(
      data(snapshot(0, 0, null), snapshot(5, 6, null)),
    );
    expect(rows[0]).toMatchObject({ pre: null, post: null, preText: '미응시' });
  });
});
