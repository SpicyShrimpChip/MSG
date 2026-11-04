import { afterEach, describe, expect, it, vi } from 'vitest';
import { api } from '@/api/axios';
import { mockCompleteConceptStudy, mockGetConceptStudies } from './study';

// 체험 모드 학습 기록: 서버로 보내지 않고(브라우저에만), 서버와 같은 규칙으로 상태가 바뀐다
describe('체험 모드 개념 학습 기록', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('학습 완료는 서버에 요청하지 않고, 같은 개념을 여러 번 눌러도 한 번만 센다', async () => {
    const send = vi.spyOn(api, 'request');
    const first = await mockCompleteConceptStudy(1, 'digital-signature');
    const again = await mockCompleteConceptStudy(1, 'digital-signature');

    expect(first.status).toBe('studied');
    expect(again.studyCount).toBe(1);
    expect(again.firstCompletedAt).toBe(first.firstCompletedAt);
    expect(send).not.toHaveBeenCalled();
  });

  it('복습 필요 개념을 다시 완료하면 학습 완료로 돌아가고 횟수가 늘며 예전 결과는 이력에 남는다', async () => {
    const before = (await mockGetConceptStudies(1)).find(
      (study) => study.conceptId === 'public-key',
    );
    expect(before?.status).toBe('review_needed');

    const after = await mockCompleteConceptStudy(1, 'public-key');
    expect(after.status).toBe('studied');
    expect(after.studyCount).toBe(2);
    expect(after.checkHistory).toHaveLength(1);
  });

  it('이해 확인된 개념은 다시 눌러도 바뀌지 않고, 없는 개념은 거절한다', async () => {
    const hash = await mockCompleteConceptStudy(2, 'hash');
    expect(hash.status).toBe('understood');
    expect(hash.studyCount).toBe(1);
    await expect(mockCompleteConceptStudy(1, 'none')).rejects.toThrow();
  });
});
