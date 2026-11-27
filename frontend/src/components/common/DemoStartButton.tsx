import type { ReactNode } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { DEMO_AVAILABLE, useAuthStore, useDemoStore } from '@/store';
import { demoStartVisible } from './demoStartVisible';

interface DemoStartButtonProps {
  className?: string;
  // 체험을 시작하고 이동할 화면. 기본은 학습 대시보드
  to?: string;
  children?: ReactNode;
}

// "로그인 없이 체험해 보기": 체험 모드를 켜고 예시 데이터 화면으로 간다.
// 보이는 조건은 demoStartVisible(체험 중·로그인·세션 되살리는 중이면 숨김). 모양은 놓는 자리에서 className으로 정한다
function DemoStartButton({
  className,
  to = '/analysis',
  children = '로그인 없이 체험해 보기',
}: DemoStartButtonProps) {
  const navigate = useNavigate();
  const active = useDemoStore((state) => state.active);
  const start = useDemoStore((state) => state.start);
  const status = useAuthStore((state) => state.status);
  const queryClient = useQueryClient();

  if (!demoStartVisible({ available: DEMO_AVAILABLE, active, status }))
    return null;

  return (
    <button
      type="button"
      onClick={() => {
        // 서버에서 받아 둔 데이터가 예시 화면에 섞이지 않게 캐시를 비운다 (끝낼 때는 메인을 새로 불러온다)
        queryClient.clear();
        start();
        navigate(to);
      }}
      className={className}
    >
      {children}
    </button>
  );
}

export default DemoStartButton;
