import type { ReactNode } from 'react';
import AiBadge from './AiBadge';

interface AiNoteProps {
  // 배지 옆 제목. 예: '이 순서인 이유'
  title: string;
  children: ReactNode;
}

// AI가 쓴 설명. 왼쪽 보라 선 + AI 배지 + 보라 제목으로 코드가 계산한 값과 구분한다.
// 보라 바탕 상자를 겹겹이 쌓으면 화면이 시끄러워져서(특히 다크 모드) 바탕은 칠하지 않는다
function AiNote({ title, children }: AiNoteProps) {
  return (
    <div className="flex flex-col gap-1.5 border-l-2 border-ai/50 py-1 pl-4">
      <div className="flex items-center gap-2">
        <AiBadge />
        <p className="text-body font-semibold text-ai">{title}</p>
      </div>
      <div className="text-body leading-relaxed text-ink">{children}</div>
    </div>
  );
}

export default AiNote;
