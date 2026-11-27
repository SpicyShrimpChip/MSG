import { describe, expect, it } from 'vitest';
import type { MasteryRecord } from '@/types';
import {
  describeLevelTrend,
  describeMasteryChange,
  formulaMajor,
  quizRateText,
} from './mastery';

const record = (overrides: Partial<MasteryRecord>): MasteryRecord => ({
  recordedAt: '2026-11-05T10:00:00',
  level: 'insufficient_evidence',
  quizGradedCount: 0,
  quizRate: null,
  conversationCount: 0,
  conversationStatus: null,
  formulaVersion: '1',
  ...overrides,
});

describe('describeMasteryChange', () => {
  it('첫 기록은 단계와 근거를 그대로 쓴다', () => {
    const result = describeMasteryChange(
      null,
      record({ quizGradedCount: 1, quizRate: 1 }),
    );
    expect(result.change).toBe('첫 기록 · 근거 부족');
    expect(result.reasons).toEqual(['퀴즈 1문제(얻은 점수 100%)', '산식 v1']);
    expect(result.date).toBe('11/5');
  });

  it('단계가 바뀌면 앞뒤 단계와 더 채점한 문제 수를 쓴다', () => {
    const previous = record({ quizGradedCount: 1, quizRate: 1 });
    const current = record({
      level: 'needs_work',
      quizGradedCount: 4,
      quizRate: 0.25,
      conversationCount: 2,
      conversationStatus: 'not_understood',
    });
    const result = describeMasteryChange(previous, current);
    expect(result.change).toBe('근거 부족 → 보완 필요');
    expect(result.reasons).toEqual([
      '퀴즈 3문제 더 채점(지금까지 4문제, 얻은 점수 25%)',
      '대화 2개 모델 판단 “이해 부족”',
      '산식 v1',
    ]);
  });

  it('산식 v2 기록은 문제별 평균 득점률로 쓴다', () => {
    const result = describeMasteryChange(
      null,
      record({ quizGradedCount: 3, quizRate: 1 / 3, formulaVersion: '2' }),
    );
    expect(result.reasons).toEqual([
      '퀴즈 3문제(문제별 평균 득점률 33%)',
      '산식 v2',
    ]);
  });

  it('산식 버전이 바뀌면 비교하기 어렵다고 알린다', () => {
    const result = describeMasteryChange(
      record({ formulaVersion: '1' }),
      record({ formulaVersion: '2' }),
    );
    expect(result.change).toBe('근거 부족 그대로');
    expect(result.reasons).toEqual([
      '반영한 퀴즈·대화 없음',
      '산식 v1 → v2 (앞 기록과 바로 비교하기 어려워요)',
    ]);
  });

  it('산식 버전은 지문 없이 앞 번호만 보여 준다', () => {
    const same = describeMasteryChange(
      record({ formulaVersion: '2-a1b3c9d2' }),
      record({ formulaVersion: '2-a1b3c9d2' }),
    );
    expect(same.reasons).toEqual(['반영한 퀴즈·대화 없음', '산식 v2']);

    const fromOld = describeMasteryChange(
      record({ formulaVersion: '1' }),
      record({ formulaVersion: '2-a1b3c9d2' }),
    );
    expect(fromOld.reasons).toEqual([
      '반영한 퀴즈·대화 없음',
      '산식 v1 → v2 (앞 기록과 바로 비교하기 어려워요)',
    ]);
  });

  it('같은 산식에서 기준값만 바뀌면 그렇게 알린다', () => {
    const result = describeMasteryChange(
      record({ formulaVersion: '2-a1b3c9d2' }),
      record({ formulaVersion: '2-ffff0000' }),
    );
    expect(result.reasons).toEqual([
      '반영한 퀴즈·대화 없음',
      '산식 v2, 기준값 조정 (앞 기록과 바로 비교하기 어려워요)',
    ]);
  });
});

describe('formulaMajor', () => {
  it('산식 버전에서 지문 앞의 번호만 꺼낸다', () => {
    expect(formulaMajor('2-a1b3c9d2')).toBe('2');
    expect(formulaMajor('1')).toBe('1');
  });
});

describe('quizRateText', () => {
  it('산식 v1 기록은 점수 합 비율, 그 뒤는 문제별 평균 득점률로 쓴다', () => {
    expect(quizRateText(0.25, '1')).toBe('얻은 점수 25%');
    expect(quizRateText(0.25, '1-0f3a9c21')).toBe('얻은 점수 25%');
    expect(quizRateText(1 / 3, '2')).toBe('문제별 평균 득점률 33%');
    expect(quizRateText(1 / 3, '2-a1b3c9d2')).toBe('문제별 평균 득점률 33%');
  });
});

describe('describeLevelTrend', () => {
  it('바로 앞 기록보다 오르거나 내려간 단계 수를 쓴다', () => {
    expect(
      describeLevelTrend([
        record({ level: 'needs_work', recordedAt: '2026-11-03T09:00:00' }),
        record({ level: 'normal' }),
      ]),
    ).toBe('11/3보다 한 단계 올랐어요');
    expect(
      describeLevelTrend([
        record({ level: 'strong', recordedAt: '2026-11-03T09:00:00' }),
        record({ level: 'needs_work' }),
      ]),
    ).toBe('11/3보다 두 단계 내려갔어요');
  });

  it('근거 부족이 끼거나 산식이 바뀌거나 같으면 알리지 않는다', () => {
    expect(
      describeLevelTrend([record({}), record({ level: 'normal' })]),
    ).toBeNull();
    expect(
      describeLevelTrend([
        record({ level: 'needs_work', formulaVersion: '1' }),
        record({ level: 'normal', formulaVersion: '2' }),
      ]),
    ).toBeNull();
    // 앞 번호가 같아도 숫자 설정 지문이 다르면 비교하지 않는다
    expect(
      describeLevelTrend([
        record({ level: 'needs_work', formulaVersion: '2-a1b3c9d2' }),
        record({ level: 'normal', formulaVersion: '2-0f3a9c21' }),
      ]),
    ).toBeNull();
    expect(
      describeLevelTrend([
        record({ level: 'normal' }),
        record({ level: 'normal' }),
      ]),
    ).toBeNull();
    expect(describeLevelTrend([record({ level: 'normal' })])).toBeNull();
  });
});
