import {
  BookCheck,
  BookOpen,
  CircleAlert,
  CircleCheck,
  CircleHelp,
} from 'lucide-react';
import type { StudyStatus } from '@/types';
import { STUDY_STATUS_LABEL, cn } from '@/utils';

const TONE: Record<StudyStatus, string> = {
  not_studied: 'border border-line-strong text-ink-soft',
  studied: 'bg-primary-soft text-primary',
  understood: 'bg-strength-soft text-strength',
  review_needed: 'bg-weakness-soft text-weakness',
  needs_check: 'border border-dashed border-line-strong text-ink-soft',
};

const ICON = {
  not_studied: BookOpen,
  studied: BookCheck,
  understood: CircleCheck,
  review_needed: CircleAlert,
  needs_check: CircleHelp,
} as const;

// 개념 학습 상태 표시 (학습 전·학습 완료·이해 확인·복습 필요·추가 확인 필요). 서버 코드 규칙 값이고, 색만으로 알리지 않게 아이콘·글자를 함께 쓴다
function StudyStatusBadge({
  status,
  className,
}: {
  status: StudyStatus;
  className?: string;
}) {
  const Icon = ICON[status];
  return (
    <span
      className={cn(
        'inline-flex shrink-0 items-center gap-1 rounded-full px-2.5 py-1 text-caption font-medium',
        TONE[status],
        className,
      )}
    >
      <Icon size={14} aria-hidden="true" />
      {STUDY_STATUS_LABEL[status]}
    </span>
  );
}

export default StudyStatusBadge;
