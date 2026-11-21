// 개인정보 처리방침·이용약관처럼 조항으로 된 법적 고지 문서의 본문.
// 한눈에 보기(해요체 요약) → 머리말 → 초안 안내 → 목차 → 조항 순서로 그린다. 제목은 페이지의 SubPage가 맡는다.

export type LegalBlock =
  | { type: 'p'; text: string }
  | { type: 'ul'; items: string[] }
  | { type: 'table'; head: string[]; rows: string[][] };

export interface LegalSection {
  id: string;
  title: string;
  blocks: LegalBlock[];
}

interface LegalDocumentProps {
  /** 처음 보는 이용자를 위한 요약. 본문과 어긋나지 않게 함께 고친다 */
  summary: string[];
  intro: string;
  /** 확정 전 문서임을 알리는 문장. 확정되면 빈 문자열로 둔다 */
  draftNotice: string;
  sections: LegalSection[];
}

function SectionBlock({ block }: { block: LegalBlock }) {
  if (block.type === 'p') {
    return <p className="leading-relaxed text-ink-soft">{block.text}</p>;
  }
  if (block.type === 'ul') {
    return (
      <ul className="flex list-disc flex-col gap-1.5 pl-5 leading-relaxed text-ink-soft">
        {block.items.map((item) => (
          <li key={item}>{item}</li>
        ))}
      </ul>
    );
  }
  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-150 border-collapse border-t-2 border-heading text-body">
        <thead>
          <tr className="bg-surface-muted text-caption text-ink-soft">
            {block.head.map((cell) => (
              <th
                key={cell}
                scope="col"
                className="border-b border-line px-3.5 py-3 text-left font-bold"
              >
                {cell}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {block.rows.map((row) => (
            <tr key={row.join('|')}>
              {row.map((cell, index) => (
                <td
                  key={block.head[index]}
                  className={`border-b border-line px-3.5 py-3 align-top leading-relaxed ${index === 0 ? 'font-bold text-heading' : 'text-ink-soft'}`}
                >
                  {cell}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function LegalDocument({
  summary,
  intro,
  draftNotice,
  sections,
}: LegalDocumentProps) {
  return (
    <>
      <section
        aria-labelledby="legal-summary-title"
        className="max-w-3xl rounded-2xl bg-section p-5"
      >
        <h2 id="legal-summary-title" className="mb-2 font-bold text-heading">
          한눈에 보기
        </h2>
        <ul className="flex list-disc flex-col gap-1.5 pl-5 leading-relaxed text-ink-soft">
          {summary.map((item) => (
            <li key={item}>{item}</li>
          ))}
        </ul>
      </section>

      <p className="max-w-3xl leading-relaxed text-ink-soft">{intro}</p>

      {draftNotice && (
        <p className="rounded-xl bg-surface-muted px-4 py-3 text-body text-ink-soft">
          {draftNotice}
        </p>
      )}

      <nav aria-label="목차" className="rounded-2xl border border-line p-5">
        <p className="mb-2 font-bold text-heading">목차</p>
        <ol className="grid gap-x-6 gap-y-1.5 text-body sm:grid-cols-2">
          {sections.map((section) => (
            <li key={section.id}>
              <a
                href={`#${section.id}`}
                className="text-ink-soft hover:text-primary hover:underline"
              >
                {section.title}
              </a>
            </li>
          ))}
        </ol>
      </nav>

      <div className="flex max-w-3xl flex-col gap-10">
        {sections.map((section) => (
          <section
            key={section.id}
            id={section.id}
            aria-labelledby={`${section.id}-title`}
            className="flex scroll-mt-24 flex-col gap-3"
          >
            <h2
              id={`${section.id}-title`}
              className="text-xl font-extrabold text-heading"
            >
              {section.title}
            </h2>
            {section.blocks.map((block, index) => (
              // 고정된 문서 내용이라 순서가 바뀌지 않는다
              // eslint-disable-next-line react-x/no-array-index-key
              <SectionBlock key={index} block={block} />
            ))}
          </section>
        ))}
      </div>
    </>
  );
}

export default LegalDocument;
