import { Sparkles } from 'lucide-react';
import type { DashboardLayout } from '@/types';

interface LayoutSummaryProps {
  layout: DashboardLayout;
  // 사용자가 AI 배치 대신 기본 배치를 고른 상태
  usingDefault: boolean;
  onToggleDefault: () => void;
}

// 동적 배치 전체 이유(AI, 보라색)와 배치할 때 본 숫자(코드, 파란색)를 한 줄 띠로 보여 준다.
// AI 배치가 마음에 들지 않으면 기본 배치로 되돌릴 수 있다
function LayoutSummary({
  layout,
  usingDefault,
  onToggleDefault,
}: LayoutSummaryProps) {
  if (!layout.explanation) return null;
  const { aiSummary, basis } = layout.explanation;

  const numbers = [
    { label: '이해 부족 개념', value: `${basis.notUnderstoodCount}개` },
    { label: '의견이 갈린 개념', value: `${basis.disagreementCount}개` },
    { label: '분석한 대화', value: `${basis.conversationCount}개` },
  ];

  return (
    <section
      aria-label="AI 배치 요약"
      className="flex flex-wrap items-center gap-x-4 gap-y-2.5 rounded-xl border border-ai-border bg-surface-raised px-4.5 py-3.5"
    >
      <span className="inline-flex items-center gap-1.5 rounded-full bg-ai-chip px-2.5 py-1 text-caption font-extrabold text-ai">
        <Sparkles size={13} aria-hidden="true" />
        AI 배치
      </span>
      <p className="min-w-0 flex-[1_1_380px] text-body leading-relaxed text-heading">
        {usingDefault
          ? '기본 배치로 보고 있어요. AI가 정한 배치로 돌아가려면 오른쪽을 눌러 주세요.'
          : aiSummary}
      </p>
      {!usingDefault && (
        <div className="flex flex-wrap items-center gap-1.5 text-caption text-ink-soft">
          <span>배치할 때 본 숫자</span>
          <ul className="flex flex-wrap gap-1.5">
            {numbers.map((item) => (
              <li
                key={item.label}
                className="rounded-md bg-primary-soft px-2 py-0.5"
              >
                {item.label}{' '}
                <strong className="text-heading">{item.value}</strong>
              </li>
            ))}
          </ul>
        </div>
      )}
      <button
        type="button"
        onClick={onToggleDefault}
        className="text-caption font-bold text-ink-soft underline-offset-4 hover:text-heading hover:underline print:hidden"
      >
        {usingDefault ? 'AI 배치로 보기' : '기본 배치로 보기'}
      </button>
    </section>
  );
}

export default LayoutSummary;
