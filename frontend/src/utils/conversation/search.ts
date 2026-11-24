import { compareLatestFirst, lowerCasedTextOf, oneLine } from './shared';
import type { ParsedConversation } from './types';

// 대화 고르기 화면의 검색·정렬. 모두 브라우저 안에서만 한다

export type ConversationOrder = 'latest' | 'title';

export interface SearchHit {
  inTitle: boolean;
  // 제목에 없고 본문에서 찾았을 때, 처음 찾은 곳의 앞뒤 한 줄
  snippet?: { before: string; match: string; after: string };
}

// 본문에서 찾은 곳 앞뒤로 보여 줄 글자 수
const SNIPPET_CONTEXT_LENGTH = 24;

// 제목이나 본문에 검색어가 있으면 어디서 찾았는지, 없으면 null (대소문자 무시).
// 소문자 글은 대화마다 한 번만 만들어 기억해 둔다(검색어를 칠 때마다 모든 메시지를 다시 바꾸지 않게)
export function searchConversation(
  conversation: ParsedConversation,
  query: string,
): SearchHit | null {
  const needle = query.trim().toLowerCase();
  if (!needle) return { inTitle: false };
  const lower = lowerCasedTextOf(conversation);
  if (lower.title.includes(needle)) return { inTitle: true };

  for (const [messageIndex, { text }] of conversation.messages.entries()) {
    const index = lower.messages[messageIndex].indexOf(needle);
    if (index < 0) continue;
    const start = Math.max(0, index - SNIPPET_CONTEXT_LENGTH);
    const end = index + needle.length;
    return {
      inTitle: false,
      snippet: {
        before:
          (start > 0 ? '…' : '') +
          oneLine(text.slice(start, index)).trimStart(),
        match: text.slice(index, end),
        after:
          oneLine(text.slice(end, end + SNIPPET_CONTEXT_LENGTH)).trimEnd() +
          (end + SNIPPET_CONTEXT_LENGTH < text.length ? '…' : ''),
      },
    };
  }
  return null;
}

// 최신순(날짜 없는 대화는 맨 뒤) 또는 가나다순. 원래 배열은 바꾸지 않는다
export function sortConversations<T>(
  items: T[],
  conversationOf: (item: T) => ParsedConversation,
  order: ConversationOrder,
): T[] {
  return [...items].sort((a, b) => {
    const x = conversationOf(a);
    const y = conversationOf(b);
    if (order === 'title') return x.title.localeCompare(y.title, 'ko');
    return compareLatestFirst(x, y);
  });
}
