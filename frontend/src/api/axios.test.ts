import {
  AxiosError,
  type AxiosAdapter,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from 'axios';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useAuthStore } from '@/store/useAuthStore';
import { api, REFRESH_LOCK_NAME, refreshAccessToken } from './axios';
import { request } from './request';

// 가짜 서버: validToken이 붙은 요청만 성공, /auth/refresh는 refreshOk에 따라 새 토큰을 주거나 401
interface Call {
  url?: string;
  auth?: string;
  requestedWith?: string;
}

const originalAdapter = api.defaults.adapter;
let calls: Call[];
let refreshCount: number;

const respond = (
  config: InternalAxiosRequestConfig,
  data: unknown,
): AxiosResponse => ({
  data: { success: true, message: '', data },
  status: 200,
  statusText: 'OK',
  headers: {},
  config,
});

const unauthorized = (config: InternalAxiosRequestConfig) =>
  Promise.reject(
    new AxiosError('Unauthorized', 'ERR_BAD_REQUEST', config, null, {
      data: {
        success: false,
        message: '로그인이 필요해요. 다시 로그인해 주세요.',
        data: null,
      },
      status: 401,
      statusText: 'Unauthorized',
      headers: {},
      config,
    }),
  );

function startServer({ refreshOk }: { refreshOk: boolean }) {
  const adapter: AxiosAdapter = async (config) => {
    const auth = config.headers.Authorization as string | undefined;
    calls.push({
      url: config.url,
      auth,
      requestedWith: config.headers['X-Requested-With'] as string | undefined,
    });
    if (config.url === '/auth/refresh') {
      refreshCount += 1;
      // 응답이 늦게 오는 동안 다른 요청이 같은 갱신을 기다리는지 보기 위해 한 박자 쉰다
      await new Promise((resolve) => setTimeout(resolve, 10));
      return refreshOk
        ? respond(config, { accessToken: 'new-token' })
        : unauthorized(config);
    }
    if (auth === 'Bearer new-token') return respond(config, 'ok');
    return unauthorized(config);
  };
  api.defaults.adapter = adapter;
}

beforeEach(() => {
  calls = [];
  refreshCount = 0;
  useAuthStore.setState({ status: 'unknown', accessToken: null });
});

afterEach(() => {
  api.defaults.adapter = originalAdapter;
  vi.unstubAllGlobals();
});

describe('Access Token 만료 처리', () => {
  it('401이면 한 번 갱신하고 새 토큰으로 원래 요청을 다시 보낸다', async () => {
    startServer({ refreshOk: true });
    useAuthStore.getState().setAccessToken('expired-token');

    await expect(request<string>({ url: '/courses' })).resolves.toBe('ok');

    expect(calls.map((call) => [call.url, call.auth])).toEqual([
      ['/courses', 'Bearer expired-token'],
      ['/auth/refresh', 'Bearer expired-token'],
      ['/courses', 'Bearer new-token'],
    ]);
    expect(useAuthStore.getState().accessToken).toBe('new-token');
  });

  it('여러 요청이 동시에 401을 받아도 갱신은 한 번만 보낸다', async () => {
    startServer({ refreshOk: true });
    useAuthStore.getState().setAccessToken('expired-token');

    const results = await Promise.all([
      request<string>({ url: '/courses' }),
      request<string>({ url: '/materials' }),
      request<string>({ url: '/analysis/dashboard' }),
    ]);

    expect(results).toEqual(['ok', 'ok', 'ok']);
    expect(refreshCount).toBe(1);
  });

  it('갱신도 실패하면 로그아웃 상태로 바꾸고 서버 문구로 오류를 낸다', async () => {
    startServer({ refreshOk: false });
    useAuthStore.getState().setAccessToken('expired-token');

    await expect(request({ url: '/courses' })).rejects.toMatchObject({
      name: 'ApiError',
      status: 401,
      message: '로그인이 필요해요. 다시 로그인해 주세요.',
    });
    expect(useAuthStore.getState()).toMatchObject({
      status: 'anonymous',
      accessToken: null,
    });
  });

  it('토큰 없이 보낸 요청과 로그인 실패는 갱신하지 않는다', async () => {
    startServer({ refreshOk: true });

    await expect(request({ url: '/courses' })).rejects.toMatchObject({
      status: 401,
    });
    useAuthStore.getState().setAccessToken('expired-token');
    await expect(
      request({ method: 'POST', url: '/auth/login', data: {} }),
    ).rejects.toMatchObject({ status: 401 });

    expect(refreshCount).toBe(0);
  });

  it('모든 요청에 CSRF 방어용 X-Requested-With 헤더를 붙인다', async () => {
    startServer({ refreshOk: true });
    useAuthStore.getState().setAccessToken('new-token');

    await request({ url: '/courses' });

    expect(calls[0].requestedWith).toBe('XMLHttpRequest');
  });
});

// 가짜 Web Locks: 같은 이름의 잠금을 요청한 순서대로 하나씩 준다 (탭 여러 개를 흉내 낸다)
function stubLocks({ fail = false } = {}) {
  const requested: string[] = [];
  let tail: Promise<unknown> = Promise.resolve();
  const locks = {
    request(name: string, callback: () => Promise<unknown>) {
      requested.push(name);
      if (fail) return Promise.reject(new Error('SecurityError'));
      const run = tail.then(() => callback());
      tail = run.catch(() => undefined);
      return run;
    },
  };
  vi.stubGlobal('navigator', { locks });
  return { locks, requested };
}

describe('탭끼리 갱신 차례 지키기 (Web Locks)', () => {
  it('다른 탭이 갱신 중이면 끝날 때까지 기다렸다가 보낸다', async () => {
    startServer({ refreshOk: true });
    const { locks, requested } = stubLocks();
    let releaseOtherTab = () => {};
    const otherTab = locks.request(
      REFRESH_LOCK_NAME,
      () =>
        new Promise<void>((resolve) => {
          releaseOtherTab = resolve;
        }),
    );

    const refreshing = refreshAccessToken();
    await new Promise((resolve) => setTimeout(resolve, 20));
    expect(refreshCount).toBe(0);

    releaseOtherTab();
    await otherTab;
    await expect(refreshing).resolves.toBe('new-token');
    expect(refreshCount).toBe(1);
    expect(requested).toEqual([REFRESH_LOCK_NAME, REFRESH_LOCK_NAME]);
  });

  it('한 탭 안에서는 잠금을 한 번만 요청하고 갱신도 한 번만 보낸다', async () => {
    startServer({ refreshOk: true });
    const { requested } = stubLocks();
    useAuthStore.getState().setAccessToken('expired-token');

    const results = await Promise.all([
      request<string>({ url: '/courses' }),
      request<string>({ url: '/materials' }),
    ]);

    expect(results).toEqual(['ok', 'ok']);
    expect(refreshCount).toBe(1);
    expect(requested).toEqual([REFRESH_LOCK_NAME]);
    expect(
      calls.find((call) => call.url === '/auth/refresh')?.requestedWith,
    ).toBe('XMLHttpRequest');
  });

  it('잠금을 쓸 수 없으면 기다리지 않고 바로 갱신한다', async () => {
    startServer({ refreshOk: true });
    stubLocks({ fail: true });

    await expect(refreshAccessToken()).resolves.toBe('new-token');
    expect(refreshCount).toBe(1);
  });

  it('Web Locks가 없는 브라우저도 지금처럼 갱신한다', async () => {
    startServer({ refreshOk: true });
    vi.stubGlobal('navigator', {});

    await expect(refreshAccessToken()).resolves.toBe('new-token');
    expect(refreshCount).toBe(1);
  });

  it('잠금 안에서 갱신이 실패하면 다시 보내지 않고 실패를 알린다', async () => {
    startServer({ refreshOk: false });
    stubLocks();

    await expect(refreshAccessToken()).rejects.toBeTruthy();
    expect(refreshCount).toBe(1);
  });
});
