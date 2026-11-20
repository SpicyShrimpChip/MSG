import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  getAssessmentComparison,
  getConceptStudies,
  getCourses,
  getCurriculum,
  getLatestAnalysis,
  getMaterial,
  getMaterials,
  getProviders,
  getQuiz,
  getQuizzes,
  restoreSession,
  search,
  startAnalysis,
} from '@/api';
import { api } from '@/api/axios';
import {
  DEMO_AVAILABLE,
  NO_BACKEND,
  isMockData,
  useDemoStore,
} from './useDemoStore';

// 테스트는 VITE_USE_MOCK 없이(실제 서버 빌드처럼) 돈다.
// 실제 서버 빌드에서도 체험을 켤 수 있고, 체험 중에는 서버를 절대 부르지 않는다 (처리방침: 체험 내용은 서버로 보내지 않음)
describe('실제 서버 빌드의 체험 모드', () => {
  afterEach(() => {
    useDemoStore.getState().end();
    vi.restoreAllMocks();
  });

  it('체험을 켜기 전에는 서버 데이터를 쓴다', () => {
    expect(NO_BACKEND).toBe(false);
    expect(DEMO_AVAILABLE).toBe(true);
    expect(isMockData()).toBe(false);
  });

  it('체험을 켜면 목업 데이터를 쓰고, 끄면 다시 서버 데이터를 쓴다', () => {
    useDemoStore.getState().start();
    expect(useDemoStore.getState().active).toBe(true);
    expect(isMockData()).toBe(true);

    useDemoStore.getState().end();
    expect(isMockData()).toBe(false);
  });

  it('체험 중에는 화면들이 부르는 조회와 분석이 서버로 가지 않는다', async () => {
    const send = vi.spyOn(api, 'request');
    useDemoStore.getState().start();

    await Promise.all([
      getCourses(),
      getMaterials(),
      getMaterial(1),
      getQuizzes(),
      getQuiz(1),
      getProviders(),
      getLatestAnalysis(1),
      startAnalysis(1),
      search('해시'),
      getCurriculum(1),
      getConceptStudies(1),
      getAssessmentComparison({ courseId: 1 }),
    ]);

    expect(send).not.toHaveBeenCalled();
  });

  it('로그인한 적이 있어도 체험 중 새로고침이면 세션을 서버에서 되살리지 않는다', async () => {
    // "로그인한 적 있음" 표시만 있는 저장소
    vi.stubGlobal('localStorage', {
      getItem: (key: string) => (key === 'ai-lms-has-session' ? '1' : null),
      setItem: () => undefined,
      removeItem: () => undefined,
      key: () => null,
      length: 0,
    });
    const refresh = vi
      .spyOn(api, 'post')
      .mockRejectedValue(new Error('서버 없음'));

    // 대조: 체험이 아니면 갱신 요청을 보낸다 (이 테스트가 실제로 요청을 잡는지 확인)
    await restoreSession();
    expect(refresh).toHaveBeenCalledTimes(1);

    useDemoStore.getState().start();
    await restoreSession();
    expect(refresh).toHaveBeenCalledTimes(1);
    vi.unstubAllGlobals();
  });
});
