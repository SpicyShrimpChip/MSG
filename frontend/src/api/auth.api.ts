import type { LoginInput, LoginResult, SignupInput, User } from '@/types';
import { hasSessionHint, useAuthStore } from '@/store/useAuthStore';
import { refreshAccessToken } from './axios';
import { request } from './request';

// 인증은 목업 없이 항상 실제 백엔드를 부른다 (가짜 로그인은 보안 흐름을 검증할 수 없다)

// React Query에서 내 정보를 담는 키. 로그아웃·다른 계정 로그인 때 캐시를 비운다
export const ME_QUERY_KEY = ['me'] as const;

export function signup(input: SignupInput): Promise<User> {
  return request<User>({ method: 'POST', url: '/auth/signup', data: input });
}

export async function login(input: LoginInput): Promise<LoginResult> {
  const result = await request<LoginResult>({
    method: 'POST',
    url: '/auth/login',
    data: input,
  });
  useAuthStore.getState().setAccessToken(result.accessToken);
  return result;
}

export async function logout(): Promise<void> {
  try {
    await request<null>({ method: 'POST', url: '/auth/logout' });
  } catch {
    // 서버에 닿지 못해도 브라우저 쪽 세션은 지운다
  } finally {
    useAuthStore.getState().clearSession();
  }
}

export function getMe(): Promise<User> {
  return request<User>({ method: 'GET', url: '/users/me' });
}

// AI 대화 분석 동의를 바꾼다. 철회(false)하면 서버가 가져온 대화와 그로부터 계산한 분석 결과를 함께 지운다
// 확정: Swagger `회원` PATCH /api/users/me/consents. 바뀐 내 정보를 돌려준다
export function updateAiConsent(aiAnalysis: boolean): Promise<User> {
  return request<User>({
    method: 'PATCH',
    url: '/users/me/consents',
    data: { aiAnalysis },
  });
}

// 탈퇴: 서버가 내 모든 데이터를 지운다. 성공하면 브라우저 쪽 세션도 지운다
// TODO: 백엔드 API가 준비되면 경로를 맞춘다 (제안: docs/frontend-requests.md "인증·마이페이지")
export async function deleteAccount(): Promise<void> {
  await request<null>({ method: 'DELETE', url: '/users/me' });
  useAuthStore.getState().clearSession();
}

// 새로고침 뒤 Refresh Token 쿠키로 로그인 상태를 되살린다. 로그인한 적이 없으면 요청하지 않는다
export async function restoreSession(): Promise<void> {
  const { clearSession } = useAuthStore.getState();
  if (!hasSessionHint()) {
    clearSession();
    return;
  }
  try {
    await refreshAccessToken();
  } catch {
    clearSession();
  }
}
