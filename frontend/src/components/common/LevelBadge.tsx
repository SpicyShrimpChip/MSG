import { CircleAlert, CircleCheck } from 'lucide-react';
import type { MasteryLevel } from '@/types';
import { cn } from '@/utils';

// 강점·보완 필요 표시. 색만으로 구분하지 않도록 아이콘과 글자를 함께 쓴다 (보통은 표시하지 않음)
function LevelBadge({ level }: { level: MasteryLevel }) {
  if (level === 'normal') return null;
  const strength = level === 'strength';
  const Icon = strength ? CircleCheck : CircleAlert;
  return (
    <span
      className={cn(
        'inline-flex shrink-0 items-center gap-1 rounded-full px-2.5 py-1 text-caption font-medium',
        strength
          ? 'bg-strength-soft text-strength'
          : 'bg-weakness-soft text-weakness',
      )}
    >
      <Icon size={14} aria-hidden="true" />
      {strength ? '강점' : '보완 필요'}
    </span>
  );
}

export default LevelBadge;
