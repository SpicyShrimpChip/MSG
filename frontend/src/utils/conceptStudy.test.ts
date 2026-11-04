import { describe, expect, it } from 'vitest';
import type { CheckAttempt } from '@/types';
import { checkQuizLink, judgeCheck, resolveStudyStatus } from './conceptStudy';

const attempt = (
  submittedAt: string,
  gradedCount: number,
  score: number,
  maxScore: number,
  waitingCount = 0,
): CheckAttempt => {
  const base = { gradedCount, score, maxScore, waitingCount };
  return {
    quizId: 1,
    submittedAt,
    rate: maxScore ? Math.round((score / maxScore) * 100) : null,
    result: judgeCheck(base),
    ...base,
  };
};

describe('judgeCheck', () => {
  it('기준(60%) 이상이면 이해 확인, 미만이면 복습 필요', () => {
    expect(
      judgeCheck({ gradedCount: 3, score: 2, maxScore: 3, waitingCount: 0 }),
    ).toBe('understood');
    expect(
      judgeCheck({ gradedCount: 3, score: 1, maxScore: 3, waitingCount: 0 }),
    ).toBe('review_needed');
  });

  it('채점 대기나 문항 부족은 틀린 답으로 세지 않고 추가 확인 필요', () => {
    expect(
      judgeCheck({ gradedCount: 2, score: 0, maxScore: 2, waitingCount: 1 }),
    ).toBe('needs_check');
    expect(
      judgeCheck({ gradedCount: 1, score: 0, maxScore: 1, waitingCount: 0 }),
    ).toBe('needs_check');
  });
});

describe('resolveStudyStatus', () => {
  it('기록이 없으면 학습 전, 학습 완료만 있으면 학습 완료', () => {
    expect(resolveStudyStatus(null, null)).toBe('not_studied');
    expect(resolveStudyStatus('2026-11-05T10:00:00', null)).toBe('studied');
  });

  it('학습 뒤 확인 퀴즈 결과로 정하고, 복습 뒤 다시 완료하면 학습 완료로 돌아간다', () => {
    const failed = attempt('2026-11-05T10:10:00', 3, 1, 3);
    expect(resolveStudyStatus('2026-11-05T10:00:00', failed)).toBe(
      'review_needed',
    );
    expect(resolveStudyStatus('2026-11-05T10:30:00', failed)).toBe('studied');
  });
});

describe('checkQuizLink', () => {
  it('확인 퀴즈 화면에 자료·개념을 넘긴다', () => {
    const link = checkQuizLink(1, 2, '인증서(pki)', '인증서(PKI)');
    const params = new URLSearchParams(link.split('?')[1]);
    expect(params.get('checkMaterialId')).toBe('2');
    expect(params.get('checkConceptId')).toBe('인증서(pki)');
    expect(params.get('concept')).toBe('인증서(PKI)');
  });
});
