import type { ReactNode } from 'react';
import { cn } from '@/utils';

interface ToggleChipProps {
  on: boolean;
  onClick: () => void;
  disabled?: boolean;
  title?: string;
  // 여럿 중 하나만 고르는 묶음(role="radiogroup" 안)이면 'radio'. 없으면 켜고 끄는 누름 버튼(aria-pressed)
  role?: 'radio';
  children: ReactNode;
}

// 눌러서 켜고 끄는 작은 알약 버튼 (퀴즈 만들기의 개념·인지 수준, 요약 목적 고르기)
function ToggleChip({
  on,
  onClick,
  disabled,
  title,
  role,
  children,
}: ToggleChipProps) {
  return (
    <button
      type="button"
      {...(role === 'radio'
        ? { role: 'radio', 'aria-checked': on }
        : { 'aria-pressed': on })}
      title={title}
      disabled={disabled}
      onClick={onClick}
      className={cn(
        'rounded-full border px-3 py-1.5 text-caption font-medium disabled:cursor-not-allowed disabled:opacity-50',
        on && !disabled
          ? 'border-primary bg-primary-soft text-primary'
          : 'border-line-strong text-ink-soft hover:bg-surface-muted',
      )}
    >
      {children}
    </button>
  );
}

export default ToggleChip;
