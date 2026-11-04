import type { ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import { USE_MOCK } from '@/api/axios';
import { useDemoStore } from '@/store';

interface DemoStartButtonProps {
  className?: string;
  // 체험을 시작하고 이동할 화면. 기본은 학습 대시보드
  to?: string;
  children?: ReactNode;
}

// "로그인 없이 체험해 보기": 체험 모드를 켜고 예시 데이터 화면으로 간다.
// 목업 모드(공개 배포)에서만 보이고, 이미 체험 중이면 숨긴다. 모양은 놓는 자리에서 className으로 정한다
function DemoStartButton({
  className,
  to = '/analysis',
  children = '로그인 없이 체험해 보기',
}: DemoStartButtonProps) {
  const navigate = useNavigate();
  const active = useDemoStore((state) => state.active);
  const start = useDemoStore((state) => state.start);

  if (!USE_MOCK || active) return null;

  return (
    <button
      type="button"
      onClick={() => {
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
