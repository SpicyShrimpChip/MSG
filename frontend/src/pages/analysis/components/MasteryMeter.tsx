import {
  CircleAlert,
  CircleCheck,
  CircleHelp,
  Contrast,
  type LucideIcon,
} from 'lucide-react';
import type { MasteryStage } from '@/types';
import { MASTERY_LABEL, cn } from '@/utils';

// 단계마다 채울 칸 수와 색. 숙련도는 코드 계산 값이라 AI 보라색을 쓰지 않는다.
// 근거 부족은 이해 부족과 다른 모양(빈 점선 칸, 물음표)으로 그린다
const STAGE_STYLE: Record<
  MasteryStage,
  { filled: number; on: string; off: string; text: string; icon: LucideIcon }
> = {
  needs_work: {
    filled: 1,
    on: 'bg-weakness',
    off: 'bg-weakness-soft',
    text: 'text-weakness',
    icon: CircleAlert,
  },
  normal: {
    filled: 2,
    on: 'bg-primary',
    off: 'bg-primary-pill',
    text: 'text-primary',
    icon: Contrast,
  },
  strong: {
    filled: 3,
    on: 'bg-strength',
    off: 'bg-strength-soft',
    text: 'text-strength',
    icon: CircleCheck,
  },
  insufficient_evidence: {
    filled: 0,
    on: '',
    off: 'border border-dashed border-line-strong bg-surface-raised',
    text: 'text-ink-soft',
    icon: CircleHelp,
  },
};

const SEGMENTS = [1, 2, 3];

// 숙련도 단계 하나: 세 칸 막대 + 아이콘 + 단계 이름. 숫자 점수는 보여 주지 않는다
function MasteryMeter({
  level,
  size = 'sm',
}: {
  level: MasteryStage;
  size?: 'sm' | 'lg';
}) {
  const style = STAGE_STYLE[level];
  const Icon = style.icon;
  const large = size === 'lg';
  return (
    <span
      className={cn(
        'inline-flex',
        large ? 'items-center gap-3' : 'flex-col gap-1.5',
      )}
    >
      <span className="flex gap-1" aria-hidden="true">
        {SEGMENTS.map((index) => (
          <span
            key={index}
            className={cn(
              'rounded-full',
              large ? 'h-2 w-7' : 'h-1.5 w-5',
              index <= style.filled ? style.on : style.off,
            )}
          />
        ))}
      </span>
      <span
        className={cn(
          'inline-flex items-center gap-1 font-bold',
          large ? 'text-lg' : 'text-caption',
          style.text,
        )}
      >
        <Icon size={large ? 18 : 14} aria-hidden="true" />
        {MASTERY_LABEL[level]}
      </span>
    </span>
  );
}

export default MasteryMeter;
