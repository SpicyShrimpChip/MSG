import {
  CircleAlert,
  CircleCheck,
  CircleHelp,
  Contrast,
  Minus,
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
  'inline-flex shrink-0 items-center gap-1 rounded-full px-2.5 py-0.5 text-caption font-semibold';

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

// 개념 목록 한 줄의 모델 판단 표식 한 칸: 위에서부터 모델 이름, 판단 아이콘 칸, 짧은 판단 글자.
// 알약 대신 같은 크기 칸을 모델 수만큼 나란히 두어, 같음·갈림이 모양으로 한눈에 보이게 한다.
// 색만으로 구분하지 않도록 아이콘(체크·반원·느낌표·물음표)과 글자를 함께 두고, DOM 순서대로 "GPT 부분 이해"로 읽힌다
const MARK_STYLE: Record<UnderstandingStatus, string> = {
  understood: 'bg-strength-soft text-strength',
  partial: 'bg-primary-soft text-primary',
  not_understood: 'bg-weakness-soft text-weakness',
  insufficient_evidence:
    'border border-dashed border-line-strong bg-surface-raised text-ink-soft',
};

export function ModelVerdictMark({
  provider,
  status,
  label,
}: {
  provider: string;
  status: UnderstandingStatus | null;
  // 칸 아래 짧은 판단 글자 (verdictMarkLabel: 연결 안 됨·판단 없음 포함)
  label: string;
}) {
  const Icon = status ? STATUS_STYLE[status].icon : Minus;
  return (
    <span className="flex w-16 flex-col items-center gap-1">
      <span className="text-[11px] leading-none text-ink-muted">
        {provider}
      </span>
      <span
        className={cn(
          'flex h-6 w-full items-center justify-center rounded-md',
          status
            ? MARK_STYLE[status]
            : 'border border-dashed border-line-strong text-ink-muted',
        )}
      >
        <Icon size={14} aria-hidden="true" />
      </span>
      <span className="text-caption leading-none whitespace-nowrap text-ink-soft">
        {label}
      </span>
    </span>
  );
}
