import { Sparkles } from 'lucide-react';

// AI가 만든 내용이 있는 화면에 두는 안내 한 줄 (frontend/CLAUDE.md 색 규칙)
function AiLegend() {
  return (
    <p className="flex items-center gap-1.5 text-caption text-ai">
      <Sparkles size={14} aria-hidden="true" />
      AI 표시가 있는 내용은 AI가 작성했어요
    </p>
  );
}

export default AiLegend;
