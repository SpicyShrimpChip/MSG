import type { ReactNode } from 'react';
import { X } from 'lucide-react';
import { cn } from '@/utils';

const TONE_CLASS = {
  // 사용자가 넣은 값(키워드 등)
  neutral: 'bg-surface-muted text-ink-soft',
  // 코드가 센 숫자
  primary: 'border border-line bg-surface-raised text-heading',
} as const;

interface ChipProps {
  tone?: keyof typeof TONE_CLASS;
  // 있으면 × 버튼을 붙인다. removeLabel은 화면 읽기 프로그램용 이름
  onRemove?: () => void;
  removeLabel?: string;
  className?: string;
  children: ReactNode;
}

// 작은 알약 표시 (키워드, 숫자 등). AI 표시는 AiBadge, 강점·보완은 LevelBadge를 쓴다
function Chip({
  tone = 'neutral',
  onRemove,
  removeLabel,
  className,
  children,
}: ChipProps) {
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1 rounded-full py-0.5 text-caption',
        onRemove ? 'pr-1 pl-2.5' : 'px-2.5',
        TONE_CLASS[tone],
        className,
      )}
    >
      {children}
      {onRemove && (
        <button
          type="button"
          onClick={onRemove}
          aria-label={removeLabel}
          className="rounded-full p-0.5 text-ink-muted hover:bg-line hover:text-ink"
        >
          <X size={14} aria-hidden="true" />
        </button>
      )}
    </span>
  );
}

export default Chip;
