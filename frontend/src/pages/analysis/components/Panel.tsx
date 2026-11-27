import type { ReactNode } from 'react';
import { Card } from '@/components';
import LayoutReasonPopover from './LayoutReasonPopover';

interface PanelProps {
  title: string;
  // 제목 아래 짧은 설명
  description?: string;
  // 동적 배치에서 이 영역이 이 위치·크기인 이유 (AI). 기본 배치면 없음
  layoutReason?: string | null;
  children: ReactNode;
}

// 대시보드 영역 하나: 공통 Card + 제목 줄 + 배치 이유 말풍선
function Panel({ title, description, layoutReason, children }: PanelProps) {
  return (
    <Card as="section" className="flex h-full min-w-0 flex-col gap-4 p-6">
      <div className="flex items-start justify-between gap-3">
        <div className="flex min-w-0 flex-col gap-1">
          <h2 className="text-lg font-semibold text-heading">{title}</h2>
          {description && (
            <p className="text-caption text-ink-muted">{description}</p>
          )}
        </div>
        {layoutReason && (
          <LayoutReasonPopover title={title} reason={layoutReason} />
        )}
      </div>
      {children}
    </Card>
  );
}

export default Panel;
