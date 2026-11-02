import {
  ArrowLeftRight,
  CircleAlert,
  CircleCheck,
  CircleHelp,
  Contrast,
  type LucideIcon,
} from 'lucide-react';
import type { ComparisonOutcome, UnderstandingStatus } from '@/types';
import { UNDERSTANDING_LABEL, cn } from '@/utils';

const STATUS_STYLE: Record<
  UnderstandingStatus,
  { icon: LucideIcon; className: string }
> = {
  understood: {
    icon: CircleCheck,
    className: 'bg-strength-soft text-strength',
  },
  partial: { icon: Contrast, className: 'bg-primary-soft text-primary' },
  not_understood: {
    icon: CircleAlert,
    className: 'bg-weakness-soft text-weakness',
  },
  // 이해 부족과 같은 색·모양으로 묶지 않는다 (근거가 없다는 뜻이지 모른다는 뜻이 아니다)
  insufficient_evidence: {
    icon: CircleHelp,
    className:
      'border border-dashed border-line-strong bg-surface-muted text-ink-soft',
  },
};

const BADGE_CLASS =
  'inline-flex shrink-0 items-center gap-1 rounded-full px-2.5 py-0.5 text-caption font-bold';

// 판단 하나. 색만으로 구분하지 않도록 아이콘과 글자를 함께 쓴다
export function UnderstandingBadge({
  status,
}: {
  status: UnderstandingStatus;
}) {
  const { icon: Icon, className } = STATUS_STYLE[status];
  return (
    <span className={cn(BADGE_CLASS, className)}>
      <Icon size={14} aria-hidden="true" />
      {UNDERSTANDING_LABEL[status]}
    </span>
  );
}

// 개념의 비교 결과. 다수 의견이 있으면 그 판단, 모두 다르면 "의견이 갈렸어요"
export function ComparisonBadge({
  outcome,
  status,
}: {
  outcome: ComparisonOutcome;
  status: UnderstandingStatus | null;
}) {
  if (status) return <UnderstandingBadge status={status} />;
  if (outcome === 'disagreement') {
    return (
      <span
        className={cn(
          BADGE_CLASS,
          'border border-line-strong bg-surface-raised text-heading',
        )}
      >
        <ArrowLeftRight size={14} aria-hidden="true" />
        의견이 갈렸어요
      </span>
    );
  }
  return (
    <span className={cn(BADGE_CLASS, 'bg-surface-muted text-ink-muted')}>
      판단 없음
    </span>
  );
}
