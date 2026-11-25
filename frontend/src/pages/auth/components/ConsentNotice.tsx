import type { Notice } from './consentNotices';

// 개인정보 안내 항목(consentNotices.ts)을 항목·내용 표로 보여 준다
export function NoticeList({ items }: { items: Notice[] }) {
  return (
    <dl className="grid grid-cols-[auto_minmax(0,1fr)] gap-x-4 gap-y-1.5 rounded-xl bg-surface-muted px-4 py-3 text-body">
      {items.map((item) => (
        <div key={item.label} className="contents">
          <dt className="font-bold text-heading">{item.label}</dt>
          <dd className="text-ink-soft">{item.value}</dd>
        </div>
      ))}
    </dl>
  );
}
