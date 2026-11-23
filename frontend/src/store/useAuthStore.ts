import { create } from 'zustand';

// unknown: 새로고침 뒤 세션을 되살리는 중 / authenticated: 로그인 / anonymous: 로그아웃
export type AuthStatus = 'unknown' | 'authenticated' | 'anonymous';

interface AuthState {
  status: AuthStatus;
  // Access Token은 메모리에만 둔다 (localStorage에 저장하지 않는다). 새로고침하면 Refresh Token 쿠키로 다시 받는다
  accessToken: string | null;
  setAccessToken: (accessToken: string) => void;
  clearSession: () => void;
}

// 새로고침 뒤 세션 복원을 시도할지 정하는 표시. 토큰이 아니라 "로그인한 적 있음"만 저장한다
// (로그인하지 않은 사람은 갱신 요청을 보내지 않도록)
const SESSION_HINT_KEY = 'msg-has-session';

export const hasSessionHint = () => {
  try {
    return localStorage.getItem(SESSION_HINT_KEY) === '1';
  } catch {
    return false;
  }
};

const setSessionHint = (on: boolean) => {
  try {
    if (on) localStorage.setItem(SESSION_HINT_KEY, '1');
    else localStorage.removeItem(SESSION_HINT_KEY);
  } catch {
    // 저장소를 쓸 수 없는 환경(비공개 창 등)에서는 새로고침 뒤 다시 로그인한다
  }
};

// 로그인 세션 (서버 데이터인 내 정보는 React Query의 ['me']로 다룬다)
export const useAuthStore = create<AuthState>()((set) => ({
  status: 'unknown',
  accessToken: null,
  setAccessToken: (accessToken) => {
    setSessionHint(true);
    set({ accessToken, status: 'authenticated' });
  },
  clearSession: () => {
    setSessionHint(false);
    set({ accessToken: null, status: 'anonymous' });
  },
}));
