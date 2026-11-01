import { useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { ArrowDown, ArrowUp } from 'lucide-react';
import Markdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { cn, type ConversationMessage, type ConversationSource } from '@/utils';
import {
  MARK_CLASS,
  findMaskLabelRange,
  markPattern,
  remarkMarks,
  splitMarks,
} from './marks';
import { MARKDOWN_COMPONENTS } from './markdownComponents';

// 가져온 대화를 채팅창처럼 보여 준다 (2단계 대화 보기, 3단계 가린 내용 확인에서 같이 쓴다).
// 가린 표시는 파랑, 찾은 키워드·검색어는 형광펜으로 칠한다.
// 사용자의 기록이라 AI 말풍선도 보라색(이 서비스의 AI가 쓴 것)으로 칠하지 않고 서비스 이름만 붙인다.
// HTML 문자열로 그리지 않는다: 마크다운은 react-markdown이 React 요소로 만들고, 대화 속 HTML은 그리지 않는다

const SOURCE_NAME: Record<ConversationSource, string> = {
  chatgpt: 'ChatGPT',
  claude: 'Claude',
};

// 가린 곳 목록에서 고른 가린 표시 하나를 진하게 칠하는 CSS Custom Highlight 이름 (index.css의 ::highlight)
const FOCUS_HIGHLIGHT = 'mask-focus';

// "동작 줄이기"를 켠 기기에서는 부드럽게 스크롤하지 않는다
const scrollBehavior = (): ScrollBehavior =>
  window.matchMedia('(prefers-reduced-motion: reduce)').matches
    ? 'auto'
    : 'smooth';

// 처음 보여 줄 메시지 수와 "더 보기"로 늘리는 수
const PAGE_SIZE = 30;

// 내가 쓴 글은 마크다운으로 해석하지 않고 줄바꿈을 그대로 둔다 (가린 표시·키워드만 칠한다)
function PlainText({ text, pattern }: { text: string; pattern: RegExp }) {
  const parts: ReactNode[] = splitMarks(text, pattern).map((part, i) =>
    part.kind ? (
      // 글 조각 순서는 바뀌지 않는다
      // eslint-disable-next-line react-x/no-array-index-key
      <mark key={i} className={MARK_CLASS[part.kind]}>
        {part.text}
      </mark>
    ) : (
      part.text
    ),
  );
  return <>{parts}</>;
}

interface FocusTarget {
  index: number;
  nonce: number;
  markIndex?: number;
}

interface ConversationViewProps {
  source: ConversationSource;
  // 가린 뒤의 메시지
  messages: ConversationMessage[];
  // 이 메시지로 이동해 표시한다. 같은 메시지로 다시 가려면 nonce를 바꾼다.
  // markIndex가 있으면 그 메시지 안의 가린 표시 중 그 번째 하나를 진하게 칠하고 그 자리로 스크롤한다
  focus?: FocusTarget | null;
  // 형광펜으로 칠할 단어(찾은 키워드·검색어). 있으면 "키워드 나온 메시지" 이전·다음 이동을 보여 준다
  keywords?: string[];
}

function ConversationView({
  source,
  messages,
  focus: focusProp,
  keywords = [],
}: ConversationViewProps) {
  const [pageCount, setPageCount] = useState(1);
  const containerRef = useRef<HTMLDivElement>(null);
  const itemRefs = useRef(new Map<number, HTMLLIElement>());

  // 배열이 새로 만들어져도 내용이 같으면 다시 계산하지 않도록 글자로 묶어 비교한다
  const keywordKey = keywords.join('\n');
  const words = useMemo(
    () => [
      ...new Set(
        keywordKey
          .split('\n')
          .map((word) => word.trim())
          .filter(Boolean),
      ),
    ],
    [keywordKey],
  );
  const pattern = useMemo(() => markPattern(words), [words]);
  // 키워드가 들어 있는 메시지 순서
  const hits = useMemo(() => {
    if (words.length === 0) return [];
    const lowered = words.map((word) => word.toLowerCase());
    return messages.flatMap((message, index) => {
      const text = message.text.toLowerCase();
      return lowered.some((word) => text.includes(word)) ? [index] : [];
    });
  }, [messages, words]);
  // 이전·다음으로 고른 순서(-1이면 아직 안 고름)와 누른 횟수(같은 곳으로 다시 이동할 때 쓴다)
  const [nav, setNav] = useState({ cursor: -1, count: 0 });
  // 키워드가 바뀌어 목록이 줄면 처음 상태로 본다
  const cursor = nav.cursor < hits.length ? nav.cursor : -1;
  const ownFocus = useMemo(
    () => (cursor < 0 ? null : { index: hits[cursor], nonce: nav.count }),
    [cursor, hits, nav.count],
  );
  const focus: FocusTarget | null = ownFocus ?? focusProp ?? null;

  // 연달아 눌러도 한 칸씩 움직이도록 이전 값을 보고 정한다
  const move = (step: 1 | -1) =>
    setNav((prev) => {
      const current = prev.cursor < hits.length ? prev.cursor : -1;
      const start = current < 0 ? (step > 0 ? -1 : hits.length) : current;
      return {
        cursor: (start + step + hits.length) % hits.length,
        count: prev.count + 1,
      };
    });

  // 이동할 메시지가 뒤쪽에 있으면 거기까지 펼친다
  const shown = Math.min(
    messages.length,
    Math.max(pageCount * PAGE_SIZE, focus ? focus.index + 1 : 0),
  );

  useEffect(() => {
    if (!focus) return;
    const container = containerRef.current;
    const item = itemRefs.current.get(focus.index);
    if (!container || !item) return;
    const behavior = scrollBehavior();
    const range =
      focus.markIndex === undefined
        ? null
        : findMaskLabelRange(item, focus.markIndex);
    // 가린 표시 하나를 짚으면 그 자리를 가운데로, 아니면 메시지 맨 위로
    const box = container.getBoundingClientRect();
    const spot = range?.getBoundingClientRect();
    container.scrollTo({
      top: spot
        ? container.scrollTop +
          spot.top -
          box.top -
          (box.height - spot.height) / 2
        : item.offsetTop - container.offsetTop - 12,
      behavior,
    });
    // 목록에서 눌렀을 때 대화 상자 자체가 화면 밖이면 같이 보이게 한다
    container.scrollIntoView({ block: 'nearest', behavior });
    item.focus({ preventScroll: true });

    // 지원하지 않는 브라우저에서는 메시지 테두리만 보인다
    const highlights = typeof CSS === 'undefined' ? undefined : CSS.highlights;
    if (range && highlights)
      highlights.set(FOCUS_HIGHLIGHT, new Highlight(range));
    return () => {
      highlights?.delete(FOCUS_HIGHLIGHT);
    };
  }, [focus]);

  const sourceName = SOURCE_NAME[source];

  return (
    <div className="flex flex-col gap-2">
      {hits.length > 0 && (
        <div className="flex flex-wrap items-center justify-between gap-2 text-caption text-ink-soft">
          <p aria-live="polite">
            {cursor < 0 ? (
              <>키워드가 나온 메시지 {hits.length}개</>
            ) : (
              <>
                키워드가 나온 메시지{' '}
                <span className="font-bold text-heading">
                  {cursor + 1} / {hits.length}
                </span>
              </>
            )}
          </p>
          <div className="flex gap-3">
            <button
              type="button"
              onClick={() => move(-1)}
              className="flex items-center gap-1 font-medium text-primary"
            >
              <ArrowUp size={14} aria-hidden="true" />
              이전
            </button>
            <button
              type="button"
              onClick={() => move(1)}
              className="flex items-center gap-1 font-medium text-primary"
            >
              <ArrowDown size={14} aria-hidden="true" />
              다음
            </button>
          </div>
        </div>
      )}
      <div
        ref={containerRef}
        className="relative max-h-[28rem] overflow-y-auto rounded-xl bg-surface-muted p-4"
      >
        <ol aria-label="대화 내용" className="flex flex-col gap-4">
          {messages.slice(0, shown).map((message, index) => {
            const focused = focus?.index === index;
            return (
              <li
                // 메시지에는 id가 없고, 순서가 바뀌거나 중간에 빠지지 않는 읽기 전용 목록이다
                // eslint-disable-next-line react-x/no-array-index-key
                key={index}
                ref={(element) => {
                  if (element) itemRefs.current.set(index, element);
                  else itemRefs.current.delete(index);
                }}
                tabIndex={-1}
                className={cn(
                  'flex flex-col gap-1 rounded-2xl outline-none',
                  message.role === 'user' ? 'items-end' : 'items-start',
                )}
              >
                <span className="sr-only">
                  {message.role === 'user' ? '나' : sourceName}
                </span>
                {message.role === 'assistant' && (
                  <span
                    aria-hidden="true"
                    className="text-caption text-ink-muted"
                  >
                    {sourceName}
                  </span>
                )}
                <div
                  className={cn(
                    'max-w-[88%] min-w-0 rounded-2xl px-4 py-2.5 text-body leading-relaxed break-words',
                    message.role === 'user'
                      ? 'rounded-br-md bg-primary whitespace-pre-wrap text-on-primary'
                      : 'rounded-bl-md border border-line bg-surface-raised text-ink',
                    focused && 'outline-2 outline-offset-2 outline-primary',
                  )}
                >
                  {message.role === 'user' ? (
                    <PlainText text={message.text} pattern={pattern} />
                  ) : (
                    <Markdown
                      remarkPlugins={[remarkGfm, [remarkMarks, { pattern }]]}
                      components={MARKDOWN_COMPONENTS}
                      skipHtml
                    >
                      {message.text}
                    </Markdown>
                  )}
                </div>
              </li>
            );
          })}
        </ol>
      </div>
      {shown < messages.length && (
        <button
          type="button"
          onClick={() => setPageCount(Math.ceil(shown / PAGE_SIZE) + 1)}
          className="self-center text-caption font-medium text-primary underline"
        >
          메시지 {Math.min(PAGE_SIZE, messages.length - shown)}개 더 보기 (
          {messages.length - shown}개 남음)
        </button>
      )}
    </div>
  );
}

export default ConversationView;
