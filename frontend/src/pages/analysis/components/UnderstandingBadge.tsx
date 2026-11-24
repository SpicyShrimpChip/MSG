import {
  CircleAlert,
  CircleCheck,
  CircleHelp,
  Contrast,
  type LucideIcon,
} from 'lucide-react';
import type { UnderstandingStatus } from '@/types';
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
  prefix,
}: {
  status: UnderstandingStatus;
  // 판단 앞에 붙일 글 (모델 이름: "GPT 이해 부족")
  prefix?: string;
}) {
  const { icon: Icon, className } = STATUS_STYLE[status];
  const label = UNDERSTANDING_LABEL[status];
  return (
    <span className={cn(BADGE_CLASS, className)}>
      <Icon size={14} aria-hidden="true" />
      {prefix ? `${prefix} ${label}` : label}
    </span>
  );
}

// 개념 목록 한 줄에 모델마다 붙이는 작은 칩: "GPT 이해 부족". 판단이 없으면 점선 칩에 이유를 쓴다
export function ModelVerdictChip({
  provider,
  status,
  missingLabel,
}: {
  provider: string;
  status: UnderstandingStatus | null;
  // 판단이 없을 때 글 (연결 안 됨 / 판단 없음)
  missingLabel?: string;
}) {
  if (!status) {
    return (
      <span
        className={cn(
          BADGE_CLASS,
          'border border-dashed border-line-strong text-ink-muted',
        )}
      >
        {provider} {missingLabel ?? '판단 없음'}
      </span>
    );
  }
  return <UnderstandingBadge status={status} prefix={provider} />;
}
