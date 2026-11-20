import axios, { isAxiosError, type InternalAxiosRequestConfig } from 'axios';
import type { ApiResponse } from '@/types';
// 화면 모드 스토어(window 사용)까지 불러오지 않도록 배럴 대신 파일을 직접 가져온다
import { useAuthStore } from '@/store/useAuthStore';

// 1. Axios 인스턴스 생성 (통신 고속도로)
export const api = axios.create({
  // 기본은 같은 출처의 /api (개발: Vite 프록시, 배포: Vercel rewrite가 백엔드로 넘김)
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  // 5초 이상 응답 없으면 에러 처리. AI 분석처럼 오래 걸리는 요청은 함수에서 timeout을 따로 늘린다
  timeout: 5000,
  headers: {
    'Content-Type': 'application/json',
    // 쿠키를 쓰는 인증 API(로그인·갱신·로그아웃)는 이 헤더가 있어야 받는다 (CSRF 방어)
    'X-Requested-With': 'XMLHttpRequest',
  },
});

// 2. 요청 인터셉터: 메모리에 있는 Access Token을 붙인다
api.interceptors.request.use((config) => {
  const { accessToken } = useAuthStore.getState();
  if (accessToken && !config.headers.Authorization) {
    config.headers.Authorization = `Bearer ${accessToken}`;
  }
  return config;
});

type RetriableConfig = InternalAxiosRequestConfig & { _authRetried?: boolean };

let refreshPromise: Promise<string> | null = null;

// Refresh Token 쿠키로 새 Access Token을 받는다. 여러 요청이 동시에 401을 받아도 갱신은 한 번만 보낸다
// (Refresh Token은 한 번 쓰면 폐기되므로, 두 번 보내면 서버가 재사용으로 보고 세션을 끊는다)
export function refreshAccessToken(): Promise<string> {
  refreshPromise ??= api
    .post<ApiResponse<{ accessToken: string }>>('/auth/refresh')
    .then(({ data: body }) => {
      if (!body.success) throw new Error(body.message);
      useAuthStore.getState().setAccessToken(body.data.accessToken);
      return body.data.accessToken;
    })
    .finally(() => {
      refreshPromise = null;
    });
  return refreshPromise;
}

// 3. 응답 인터셉터: Access Token이 만료돼 401이 오면 한 번 갱신하고 원래 요청을 다시 보낸다.
// 갱신도 실패하면 로그아웃 상태로 바꾼다 (보호된 화면은 로그인 가드가 로그인 화면으로 보낸다)
api.interceptors.response.use(
  (response) => response,
  async (error: unknown) => {
    if (
      !isAxiosError(error) ||
      error.response?.status !== 401 ||
      !error.config
    ) {
      return Promise.reject(error);
    }
    const config: RetriableConfig = error.config;
    // 토큰 없이 보낸 요청, 이미 다시 보낸 요청, 인증 API 자체(로그인 실패 등)는 갱신하지 않는다
    const skip =
      !config.headers.Authorization ||
      config._authRetried ||
      config.url?.startsWith('/auth/');
    if (skip) return Promise.reject(error);

    config._authRetried = true;
    try {
      const token = await refreshAccessToken();
      config.headers.Authorization = `Bearer ${token}`;
    } catch {
      useAuthStore.getState().clearSession();
      return Promise.reject(error);
    }
    return api.request(config);
  },
);
