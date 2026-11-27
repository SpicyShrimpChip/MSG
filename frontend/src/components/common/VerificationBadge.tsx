import {
  CircleAlert,
  CircleCheck,
  CircleHelp,
  CircleX,
  Contrast,
} from 'lucide-react';
import type { VerificationStatus } from '@/types';
import { VERIFICATION_LABEL, cn } from '@/utils';

const STYLE = {
  verified: { icon: CircleCheck, className: 'bg-strength-soft text-strength' },
  partial: { icon: Contrast, className: 'bg-primary-soft text-primary' },
  needs_check: {
    icon: CircleAlert,
    className: 'bg-weakness-soft text-weakness',
  },
  // 근거 부족은 실패·약점과 다른 모양 (점선)
  insufficient: {
    icon: CircleHelp,
    className:
      'border border-dashed border-line-strong bg-surface-muted text-ink-soft',
  },
  failed: {
    icon: CircleX,
    className: 'border border-line-strong text-ink-soft',
  },
} as const;

// 검증 상태 표시 (서버 코드 규칙 값). label이 있으면 그 글자를 쓴다(예: 원문 근거 "원문과 일치").
// 색만으로 알리지 않게 아이콘·글자를 함께 쓴다
function VerificationBadge({
  status,
  label,
  className,
}: {
  status: VerificationStatus;
  label?: string;
  className?: string;
}) {
  const { icon: Icon, className: tone } = STYLE[status];
  return (
    <span
      className={cn(
        'inline-flex shrink-0 items-center gap-1 rounded-full px-2.5 py-0.5 text-caption font-semibold',
        tone,
        className,
      )}
    >
      <Icon size={14} aria-hidden="true" />
      {label ?? VERIFICATION_LABEL[status]}
    </span>
  );
}

export default VerificationBadge;
