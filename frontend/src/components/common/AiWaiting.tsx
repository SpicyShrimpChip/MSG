import type { ReactNode } from 'react';
import type { OrbState } from 'thinking-orbs';
import { cn } from '@/utils/cn';
import AiOrb from './AiOrb';

interface AiWaitingProps {
  // 움직임 모양. 세 모델이 따로 판단하는 분석은 weaving, 그 밖의 AI 작업(만들기·채점)은 connecting
  state?: OrbState;
  // 바깥 상자가 이미 aria-live면 false (겹쳐 읽히지 않게)
  live?: boolean;
  className?: string;
  children: ReactNode;
}

// AI 응답을 오래(최대 1분 40초) 기다리는 동안 보여 주는 안내 상자: 점 구슬(AiOrb) + 안내 글.
// 금방 끝나는 서버 저장(자료 올리기·학습 완료 등)에는 쓰지 않는다(AI가 일하는 것처럼 보인다). 그때는 Spinner
function AiWaiting({
  state = 'connecting',
  live = true,
  className,
  children,
}: AiWaitingProps) {
  return (
    <div
      aria-live={live ? 'polite' : undefined}
      className={cn(
        'flex items-center gap-3 rounded-xl bg-primary-soft px-4 py-3 text-body text-heading',
        className,
      )}
    >
      <AiOrb state={state} />
      <p className="min-w-0">{children}</p>
    </div>
  );
}

export default AiWaiting;
