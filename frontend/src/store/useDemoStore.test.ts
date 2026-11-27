import { afterEach, describe, expect, it, vi } from 'vitest';
import * as apiModule from '@/api';
import { getCourses, restoreSession } from '@/api';
import { api } from '@/api/axios';
import { useAuthStore } from '@/store/useAuthStore';
import {
  DEMO_AVAILABLE,
  NO_BACKEND,
  isMockData,
  useDemoStore,
} from './useDemoStore';

// 체험 중에도 서버를 부르는 것이 맞아서 검사에서 빼는 내보내기와 이유.
// 새 API 함수는 여기에 넣지 않으면 자동으로 "체험 중 서버 호출 없음" 검사를 받는다
const AUTH_ALWAYS_SERVER =
  '인증은 목업 없이 항상 서버를 부른다(auth.api.ts). 체험 중에는 로그인 상태가 아니라 화면이 부르지 않고, 로그인하면 체험이 끝난다';
const NOT_CHECKED: Record<string, string> = {
  api: 'Axios 인스턴스 자체',
  request: '모든 API 함수가 쓰는 요청 도우미',
  unwrap: '응답 풀기 도우미 (요청 없음)',
  toApiError: '오류 바꾸기 도우미 (요청 없음)',
  ApiError: '오류 클래스',
  refreshAccessToken:
    '401 인터셉터와 restoreSession만 부른다. 체험 중 막는 것은 restoreSession 검사가 본다',
  signup: AUTH_ALWAYS_SERVER,
  login: AUTH_ALWAYS_SERVER,
  getSocialProviders:
    '체험 중에는 KakaoLoginButton이 부르지 않는다(enabled: !demoActive)',
  getPendingSocialSignup: AUTH_ALWAYS_SERVER,
  completeSocialSignup: AUTH_ALWAYS_SERVER,
  logout: AUTH_ALWAYS_SERVER,
  getMe: '로그인했을 때만 부른다(useMe의 enabled)',
  updateConsents: AUTH_ALWAYS_SERVER,
  updateLearningProfile: AUTH_ALWAYS_SERVER,
  linkSocialAccount: AUTH_ALWAYS_SERVER,
  deleteAccount: AUTH_ALWAYS_SERVER,
};

// 숫자 id 하나([1])가 아닌 인자가 필요한 함수. 목업이 받아들일 만한 값이면 되고, 목업이 오류를 내도 검사는 같다
const DEMO_CALL_ARGS: Record<string, unknown[]> = {
  saveConceptFeedback: [1, { concept: '해시 함수', feedback: 'agree' }],
  createAssessment: [{ requestKey: 'demo', materialId: 1, kind: 'pre' }],
  getAssessmentComparison: [{ courseId: 1 }],
  createNotice: [{ title: '공지', content: '내용' }],
  updateNotice: [1, { title: '공지', content: '내용' }],
  createCourse: [{ name: '새 과목', semester: '2026-2', keywords: ['해시'] }],
  updateCourse: [1, { name: '과목', semester: '2026-2', keywords: ['해시'] }],
  uploadMaterial: [
    {
      courseId: 1,
      title: '필기',
      file: new File(['필기'], '필기.txt'),
      requestKey: 'demo',
    },
  ],
  extractMaterialConcepts: [1, { materialContentConsent: true }],
  createConceptStudyNote: [
    1,
    { conceptId: 'hash-function', materialContentConsent: true },
  ],
  getStudySummary: [1, 'exam_prep'],
  generateStudySummary: [
    1,
    { purpose: 'exam_prep', materialContentConsent: true },
  ],
  createQuiz: [
    {
      requestKey: 'demo',
      courseId: 1,
      mode: 'exam_prep',
      questionTypes: ['multiple_choice'],
      questionCount: 1,
      difficulty: 'medium',
    },
  ],
  getGenerationStatus: ['demo'],
  submitQuiz: [1, [{ questionId: 1, choiceIndex: 0 }]],
  regradeQuiz: [1, 1],
  completeConceptStudy: [1, 'hash-function'],
  search: ['해시'],
  importConversations: [
    {
      importId: 'demo',
      requestIndex: 0,
      requestCount: 1,
      courseId: 1,
      conversations: [
        {
          clientId: 'c1',
          source: 'chatgpt',
          title: '해시 함수 공부',
          messages: [{ role: 'user', text: '해시 함수가 뭐야?' }],
        },
      ],
    },
  ],
};

// Axios 인스턴스의 전송 단계를 바꿔 끼워, request·get·post 어느 쪽으로 보내도 잡는다 (실제 통신은 하지 않는다)
const originalAdapter = api.defaults.adapter;
function fakeServer() {
  const adapter = vi.fn(() => Promise.reject(new Error('서버 호출')));
  api.defaults.adapter = adapter;
  return adapter;
}

// 테스트는 VITE_USE_MOCK 없이(실제 서버 빌드처럼) 돈다.
// 실제 서버 빌드에서도 체험을 켤 수 있고, 체험 중에는 서버를 절대 부르지 않는다 (처리방침: 체험 내용은 서버로 보내지 않음)
describe('실제 서버 빌드의 체험 모드', () => {
  afterEach(() => {
    useDemoStore.getState().end();
    api.defaults.adapter = originalAdapter;
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

  it('체험 중에는 @/api의 모든 API 함수(조회·쓰기)가 서버로 가지 않는다', async () => {
    const adapter = fakeServer();
    // 대조: 체험이 아니면 같은 방법으로 요청을 잡는다 (이 테스트가 실제로 요청을 볼 수 있는지 확인)
    await getCourses().catch(() => undefined);
    expect(adapter).toHaveBeenCalledTimes(1);
    adapter.mockClear();

    useDemoStore.getState().start();
    const checked: string[] = [];
    for (const [name, value] of Object.entries(apiModule)) {
      if (typeof value !== 'function' || name in NOT_CHECKED) continue;
      const call = value as (...args: unknown[]) => unknown;
      const result = await Promise.resolve()
        .then(() => call(...(DEMO_CALL_ARGS[name] ?? [1])))
        .catch(() => undefined);
      // 쿼리 설정(materialQuery 등)은 queryFn까지 불러 본다
      const queryFn = (result as { queryFn?: unknown } | undefined)?.queryFn;
      if (typeof queryFn === 'function') {
        await Promise.resolve()
          .then(() => (queryFn as (context: unknown) => unknown)({}))
          .catch(() => undefined);
      }
      checked.push(name);
      expect(
        adapter,
        `${name}이(가) 체험 중에 서버를 불렀다`,
      ).not.toHaveBeenCalled();
    }

    // 쓰기 API도 실제로 돌았는지 (목록이 비어 통과하는 일이 없게)
    expect(checked).toEqual(
      expect.arrayContaining([
        'importConversations',
        'submitQuiz',
        'uploadMaterial',
        'createCourse',
        'updateCourse',
        'deleteCourse',
        'restoreSession',
      ]),
    );
  });

  it('체험 검사에서 빼 둔 이름은 지금도 @/api에 있다', () => {
    for (const name of [
      ...Object.keys(NOT_CHECKED),
      ...Object.keys(DEMO_CALL_ARGS),
    ]) {
      expect(apiModule, `${name}이(가) @/api에 없다`).toHaveProperty(name);
    }
  });

  it('로그인한 적이 있어도 체험 중 새로고침이면 세션을 서버에서 되살리지 않는다', async () => {
    // "로그인한 적 있음" 표시만 있는 저장소
    vi.stubGlobal('localStorage', {
      getItem: (key: string) => (key === 'msg-has-session' ? '1' : null),
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

  // "로그인한 적 있음" 표시만 있는 저장소. 표시를 지우는지 removeItem으로 본다
  const stubSessionHint = () => {
    const removeItem = vi.fn();
    vi.stubGlobal('localStorage', {
      getItem: (key: string) => (key === 'msg-has-session' ? '1' : null),
      setItem: () => undefined,
      removeItem,
      key: () => null,
      length: 0,
    });
    return removeItem;
  };

  it('체험 중 새로고침은 이 탭만 익명으로 두고, 다른 탭이 쓰는 로그인 표시는 지우지 않는다', async () => {
    const removeItem = stubSessionHint();
    useDemoStore.getState().start();
    await restoreSession();
    expect(useAuthStore.getState().status).toBe('anonymous');
    expect(removeItem).not.toHaveBeenCalledWith('msg-has-session');
    vi.unstubAllGlobals();
  });

  it('체험이 아닌데 세션 갱신이 실패하면 로그인 표시를 지운다', async () => {
    const removeItem = stubSessionHint();
    vi.spyOn(api, 'post').mockRejectedValue(new Error('쿠키 만료'));
    await restoreSession();
    expect(useAuthStore.getState().status).toBe('anonymous');
    expect(removeItem).toHaveBeenCalledWith('msg-has-session');
    vi.unstubAllGlobals();
  });
});
