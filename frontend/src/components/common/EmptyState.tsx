import type { ReactNode } from 'react';
import Button from './Button';

interface EmptyStateProps {
  icon?: ReactNode;
  title: string;
  description: string;
  // 빈 화면에서 할 수 있는 다음 행동
  action?: { label: string; to: string };
}

function EmptyState({ icon, title, description, action }: EmptyStateProps) {
  return (
    <div className="flex flex-col items-center gap-3 rounded-2xl bg-surface-muted px-6 py-14 text-center">
      {icon && (
        <div className="flex size-12 items-center justify-center rounded-xl bg-primary-soft text-primary">
          {icon}
        </div>
      )}
      <p className="text-lg font-semibold text-heading">{title}</p>
      <p className="max-w-120 text-body leading-relaxed text-ink-soft">
        {description}
      </p>
      {action && (
        <Button to={action.to} className="mt-2">
          {action.label}
        </Button>
      )}
    </div>
  );
}

export default EmptyState;
