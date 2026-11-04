import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { USE_MOCK } from '@/api';
import { LoadingFallback } from '@/components';
import { MOCK_OPEN_ACCESS, useAuthStore, useDemoStore } from '@/store';

// 로그인이 필요한 화면을 감싸는 가드. 로그인하지 않았으면 로그인 화면으로 보내고, 로그인 뒤 원래 화면으로 돌아온다.
// 목업 모드(VITE_USE_MOCK=true)에는 로그인이 없으므로 "체험 모드"를 켠 사람만 들어올 수 있다
// (공개 배포에서 주소만 쳐서 예시 화면이 바로 열리지 않게). 개발 중에는 VITE_MOCK_OPEN_ACCESS=true로 가드를 끌 수 있다
function RequireAuth() {
  const status = useAuthStore((state) => state.status);
  const demoActive = useDemoStore((state) => state.active);
  const location = useLocation();

  const toLogin = (
    <Navigate
      to="/login"
      replace
      state={{ from: `${location.pathname}${location.search}` }}
    />
  );

  if (USE_MOCK) {
    return demoActive || MOCK_OPEN_ACCESS ? <Outlet /> : toLogin;
  }
  // 새로고침 뒤 세션을 되살리는 동안
  if (status === 'unknown') return <LoadingFallback />;
  if (status === 'anonymous') return toLogin;
  return <Outlet />;
}

export default RequireAuth;
