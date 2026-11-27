import { useEffect, useRef } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { refreshAccessToken } from '@/api';
import { safeReturnTo, socialLoginErrorPath } from '@/utils';

// 카카오 로그인이 끝나 이미 가입한 사용자가 돌아오는 화면 (/auth/social/done?returnTo=).
// 서버가 콜백에서 Refresh 쿠키를 심어 두었으니, 그 쿠키로 Access Token을 받아 로그인을 이어 간 뒤 원래 화면으로 간다
function SocialDonePage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [searchParams] = useSearchParams();
  const returnTo = safeReturnTo(searchParams.get('returnTo'));
  // 개발 모드(StrictMode)에서 효과가 두 번 돌아도 한 번만 진행한다
  const started = useRef(false);

  useEffect(() => {
    if (started.current) return;
    started.current = true;
    refreshAccessToken()
      .then(() => {
        // 이전 사용자의 화면 데이터가 남지 않도록 비운다 (내 정보는 다음 화면이 새로 읽는다)
        queryClient.clear();
        navigate(returnTo, { replace: true });
      })
      .catch(() => {
        navigate(socialLoginErrorPath('failed', returnTo), { replace: true });
      });
  }, [navigate, queryClient, returnTo]);

  return (
    <p role="status" className="px-4 py-20 text-center text-ink-soft">
      로그인하는 중이에요…
    </p>
  );
}

export default SocialDonePage;
