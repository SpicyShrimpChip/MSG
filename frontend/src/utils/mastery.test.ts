import { describe, expect, it } from 'vitest';
import type { MasteryRecord } from '@/types';
import { describeLevelTrend, describeMasteryChange } from './mastery';

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
    expect(
      describeLevelTrend([
        record({ level: 'normal' }),
        record({ level: 'normal' }),
      ]),
    ).toBeNull();
    expect(describeLevelTrend([record({ level: 'normal' })])).toBeNull();
  });
});
