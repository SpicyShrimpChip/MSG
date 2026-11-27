import * as m from 'motion/react-m';
import { MOTION_DURATION, MOTION_EASE } from '@/constants';
import type { MasteryLevel } from '@/types';
import { cn } from '@/utils';

interface ScoreBarProps {
  // 0~100
  value: number;
  // 개념 숙련도 막대면 단계. 보완 필요는 붉은 막대이고, 색만으로 알리지 않도록 옆에 "보완 필요" 글자를 함께 둔다
  level?: MasteryLevel;
  // 여러 막대를 차례로 차오르게 할 때 늦출 시간(초)
  delay?: number;
  className?: string;
}

// 점수·비중 막대. 코드가 계산한 값이라 파랑(primary). 숫자는 옆에 글자로 따로 보여 준다
// 처음 보일 때 0에서 값까지 차오르고, 값이 바뀌면 부드럽게 따라간다
function ScoreBar({ value, level, delay = 0, className }: ScoreBarProps) {
  const percent = Math.min(100, Math.max(0, value));
  return (
    <span
      aria-hidden="true"
      className={cn(
        'block h-2.5 overflow-hidden rounded-full bg-surface-muted',
        className,
      )}
    >
      <m.span
        className={cn(
          'block h-full rounded-full',
          level === 'weakness' ? 'bg-weakness-bar' : 'bg-primary',
        )}
        initial={{ width: 0 }}
        animate={{ width: `${percent}%` }}
        transition={{
          duration: MOTION_DURATION.slow,
          ease: MOTION_EASE,
          delay,
        }}
      />
    </span>
  );
}

export default ScoreBar;
