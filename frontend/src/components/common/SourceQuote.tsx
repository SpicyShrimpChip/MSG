import { Quote } from 'lucide-react';

// 자료 원문 인용 (서버가 원문에 있는지 확인한 문장, AI 글 아님 → 회색). 위치도 서버가 찾은 값이다
function SourceQuote({
  quote,
  location,
  label = '근거',
}: {
  quote: string;
  location: string | null;
  label?: string;
}) {
  return (
    <figure className="flex gap-2 rounded-lg bg-surface-muted p-3 text-caption text-ink">
      <Quote
        size={14}
        className="mt-0.5 shrink-0 text-ink-muted"
        aria-hidden="true"
      />
      <div className="flex min-w-0 flex-col gap-1">
        <blockquote className="break-words">{quote}</blockquote>
        <figcaption className="text-ink-soft">
          {label} · {location ?? '위치 표시 없음'} (자료 원문)
        </figcaption>
      </div>
    </figure>
  );
}

export default SourceQuote;
