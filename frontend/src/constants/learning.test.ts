import { describe, expect, it } from 'vitest';
import { quizKindLabel } from './learning';

describe('quizKindLabel', () => {
  const base = {
    mode: 'exam_prep' as const,
    mockExam: false,
    assessmentKind: null,
  };

  it('사전·사후평가 → 모의시험 → 학습 모드 순으로 하나를 고른다', () => {
    expect(
      quizKindLabel({ ...base, assessmentKind: 'pre', mockExam: true }),
    ).toBe('사전평가');
    expect(quizKindLabel({ ...base, assessmentKind: 'post' })).toBe('사후평가');
    expect(quizKindLabel({ ...base, mockExam: true })).toBe('모의시험');
    expect(quizKindLabel(base)).toBe('시험 대비');
  });

  it('학습 모드가 없으면 평가여도 표시하지 않는다', () => {
    expect(quizKindLabel({ ...base, mode: null, assessmentKind: 'pre' })).toBe(
      null,
    );
  });
});
