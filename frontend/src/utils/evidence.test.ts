import { describe, expect, it } from 'vitest';
import {
  BLOOM_LABEL,
  MODE_DEFAULT_BLOOM,
  behaviorSignal,
  describeGrounding,
  effectiveBloomLevels,
} from './evidence';

describe('인지 수준', () => {
  it('학습 목적마다 기본 수준이 다르고 낮은 수준부터다', () => {
    expect(MODE_DEFAULT_BLOOM.exam_prep[0]).toBe('remember');
    expect(MODE_DEFAULT_BLOOM.work).toContain('create');
    expect(BLOOM_LABEL.analyze).toBe('분석');
  });

  it('서술형이 없으면 창조를 빼고 정렬한다', () => {
    expect(
      effectiveBloomLevels(['create', 'apply', 'remember'], false),
    ).toEqual(['remember', 'apply']);
    expect(effectiveBloomLevels(['create', 'apply'], true)).toEqual([
      'apply',
      'create',
    ]);
  });
});

describe('학습 행동', () => {
  it('재설명 요청·심화 질문·알겠어는 판단에 쓰지 않는 신호다', () => {
    expect(behaviorSignal('re_explanation_request')).toBe('neutral');
    expect(behaviorSignal('deepening_question')).toBe('neutral');
    expect(behaviorSignal('no_signal')).toBe('neutral');
    expect(behaviorSignal('incorrect_explanation')).toBe('negative');
    expect(behaviorSignal('application')).toBe('positive');
  });
});

describe('자료 근거 요약', () => {
  const design = {
    bloomTargets: [],
    promptVersion: 'quiz-gen-v6',
    materialContentUsed: true,
    materialScope: 'p. 3',
    missingConcepts: [],
    grounding: { verified: 3, partial: 1, failed: 1, insufficient: 0 },
  };

  it('확인한 문제 수를 센다', () => {
    expect(describeGrounding(design)).toBe(
      '5문제 중 원문과 일치 3문제, 일부 일치 1문제, 근거 확인 안 됨 1문제',
    );
  });

  it('자료 본문을 쓰지 않았으면 보여 주지 않는다', () => {
    expect(
      describeGrounding({ ...design, materialContentUsed: false }),
    ).toBeNull();
    expect(describeGrounding(undefined)).toBeNull();
  });
});
