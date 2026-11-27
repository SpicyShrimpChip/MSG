import type { Components } from 'react-markdown';

// 대화 속 AI 답변의 마크다운을 그릴 때 쓰는 요소 모양
export const MARKDOWN_COMPONENTS: Components = {
  // 대화 속 링크는 새 탭으로, 이 페이지에 손대지 못하게
  a: ({ href, children }) => (
    <a
      href={href}
      target="_blank"
      rel="noopener noreferrer nofollow"
      className="text-primary underline"
    >
      {children}
    </a>
  ),
  // 다른 사이트 이미지를 불러오지 않는다 (불러오면 그 사이트에 내 접속이 알려진다)
  img: ({ alt }) => <span>[이미지{alt ? `: ${alt}` : ''}]</span>,
  h1: ({ children }) => <p className="mt-3 font-semibold">{children}</p>,
  h2: ({ children }) => <p className="mt-3 font-semibold">{children}</p>,
  h3: ({ children }) => <p className="mt-2 font-semibold">{children}</p>,
  h4: ({ children }) => <p className="mt-2 font-semibold">{children}</p>,
  p: ({ children }) => <p className="my-1.5">{children}</p>,
  ul: ({ children }) => <ul className="my-1.5 list-disc pl-5">{children}</ul>,
  ol: ({ children }) => (
    <ol className="my-1.5 list-decimal pl-5">{children}</ol>
  ),
  blockquote: ({ children }) => (
    <blockquote className="my-1.5 border-l-2 border-line-strong pl-3 text-ink-soft">
      {children}
    </blockquote>
  ),
  pre: ({ children }) => (
    <pre className="my-2 overflow-x-auto rounded-lg bg-surface-muted p-3 text-caption">
      {children}
    </pre>
  ),
  code: ({ children }) => (
    <code className="rounded bg-surface-muted px-1 font-mono text-[0.9em]">
      {children}
    </code>
  ),
  table: ({ children }) => (
    <div className="my-2 overflow-x-auto">
      <table className="w-full border-collapse text-caption">{children}</table>
    </div>
  ),
  th: ({ children }) => (
    <th className="border-b border-line-strong px-2 py-1 text-left font-semibold">
      {children}
    </th>
  ),
  td: ({ children }) => (
    <td className="border-b border-line px-2 py-1">{children}</td>
  ),
  hr: () => <hr className="my-3 border-line" />,
};
