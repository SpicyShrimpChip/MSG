import type { ReactNode } from 'react';
import AiBadge from './AiBadge';

interface AiNoteProps {
  // 배지 옆 제목. 예: '이 순서인 이유'
  title: string;
  children: ReactNode;
}

// AI가 쓴 설명 상자. 보라색 바탕 + AI 배지로 코드가 계산한 값과 구분한다
function AiNote({ title, children }: AiNoteProps) {
  return (
    <div className="flex flex-col gap-2 rounded-xl border border-ai-border bg-ai-soft p-4">
      <div className="flex items-center gap-2">
        <AiBadge />
        <p className="text-body font-semibold text-ai">{title}</p>
      </div>
      <div className="text-body leading-relaxed text-ink">{children}</div>
    </div>
  );
}

export default AiNote;
