import { useQuery } from '@tanstack/react-query';
import { getSocialProviders } from '@/api';
import { NO_BACKEND, useDemoStore } from '@/store';
import { kakaoStartUrl } from '@/utils';

export const SOCIAL_PROVIDERS_QUERY_KEY = ['social-providers'] as const;

interface KakaoLoginButtonProps {
  // 로그인 뒤 돌아갈 화면 경로 (사이트 안 경로만, 아니면 첫 화면)
  returnTo?: string;
  // 다른 로그인 방법과 나누는 "또는" 선을 버튼 위(before)·아래(after)에 둔다. 버튼이 숨으면 선도 숨는다
  divider: 'before' | 'after';
}

// 카카오 로그인 버튼. 서버에 카카오 키가 있을 때만 보인다(목록에 kakao가 있을 때).
// 백엔드가 없는 빌드·체험 중에는 묻지 않고 숨긴다. 모양은 카카오 디자인 가이드(노란 배경, 검정 말풍선, 모서리 12px)를 따른다
function KakaoLoginButton({ returnTo, divider }: KakaoLoginButtonProps) {
  const demoActive = useDemoStore((state) => state.active);
  const { data: providers } = useQuery({
    queryKey: SOCIAL_PROVIDERS_QUERY_KEY,
    queryFn: getSocialProviders,
    enabled: !NO_BACKEND && !demoActive,
    staleTime: Infinity,
    retry: false,
  });
  if (NO_BACKEND || demoActive || !providers?.includes('kakao')) return null;

  const line = (
    <div className="flex items-center gap-3 text-body text-ink-muted">
      <span aria-hidden="true" className="h-px flex-1 bg-line" />
      또는
      <span aria-hidden="true" className="h-px flex-1 bg-line" />
    </div>
  );

  return (
    <div className="flex flex-col gap-5">
      {divider === 'before' && line}
      <button
        type="button"
        onClick={() => window.location.assign(kakaoStartUrl(returnTo))}
        className="flex h-12 w-full items-center justify-center gap-2 rounded-[12px] bg-[#FEE500] px-4 text-base font-semibold text-[rgba(0,0,0,0.85)] transition-[filter] hover:brightness-95 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
      >
        <svg
          aria-hidden="true"
          viewBox="0 0 24 24"
          className="size-5 shrink-0"
          fill="#000000"
        >
          <path d="M12 3C6.48 3 2 6.54 2 10.9c0 2.8 1.86 5.27 4.66 6.67-.2.73-.74 2.68-.85 3.1-.13.52.19.51.4.37.17-.11 2.62-1.78 3.68-2.5.69.1 1.39.15 2.11.15 5.52 0 10-3.54 10-7.9S17.52 3 12 3Z" />
        </svg>
        카카오 로그인
      </button>
      {divider === 'after' && line}
    </div>
  );
}

export default KakaoLoginButton;
