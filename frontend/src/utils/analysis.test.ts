import { describe, expect, it } from 'vitest';
import type { AnalysisRun, ConceptComparison, ConceptResult } from '@/types';
import {
  countJudgingModels,
  describeComparison,
  sortByStudyOrder,
} from './analysis';

const comparison = (
  outcome: ConceptComparison['outcome'],
  judgedCount: number,
  agreeCount: number,
): ConceptComparison => ({
  outcome,
  status: null,
  judgedCount,
  agreeCount,
  calledCount: 3,
});

describe('describeComparison', () => {
  it.each([
    [comparison('unanimous', 3, 3), '3개 모두 같음'],
    [comparison('majority', 3, 2), '3개 중 2개 같음'],
    [comparison('disagreement', 2, 1), '2개가 서로 다름'],
    [comparison('single_model', 1, 1), '1개만 판단 · 비교 못 함'],
    [comparison('no_result', 0, 0), '판단한 모델 없음'],
  ])('%o → %s', (input, expected) => {
    expect(describeComparison(input)).toBe(expected);
  });

  it('정확도처럼 퍼센트로 보여 주지 않는다', () => {
    expect(describeComparison(comparison('majority', 3, 2))).not.toMatch(/%/);
  });
});

describe('countJudgingModels', () => {
  it('성공한 모델만 판단한 모델로 센다', () => {
    const run = {
      models: [
        { status: 'succeeded' },
        { status: 'failed' },
        { status: 'not_configured' },
      ],
    } as AnalysisRun;
    expect(countJudgingModels(run)).toEqual({ judged: 1, total: 3 });
  });
});

describe('sortByStudyOrder', () => {
  it('추천 순서대로 정렬하고 원래 배열은 바꾸지 않는다', () => {
    const concepts = [
      { concept: 'B', studyOrder: 2 },
      { concept: 'A', studyOrder: 1 },
    ] as ConceptResult[];
    expect(sortByStudyOrder(concepts).map((item) => item.concept)).toEqual([
      'A',
      'B',
    ]);
    expect(concepts[0].concept).toBe('B');
  });
});
