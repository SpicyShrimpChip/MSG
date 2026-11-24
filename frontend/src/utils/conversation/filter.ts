import { normalizeKeyword } from '../keywords';
import { lowerCasedTextOf } from './shared';
import type { ParsedConversation } from './types';

export interface KeywordMatch {
  conversation: ParsedConversation;
  // 이 대화에서 찾은 키워드 (등록한 순서)
  matchedKeywords: string[];
}

// 비교할 키워드: 등록한 모양(찾은 키워드로 보여 줌)과 소문자 (한 번만 바꿔 두고 대화마다 같이 쓴다)
export interface KeywordTarget {
  keyword: string;
  lower: string;
}

// 키워드의 공백을 정리하고 빈 것은 뺀다
export function keywordTargets(keywords: string[]): KeywordTarget[] {
  return keywords
    .map(normalizeKeyword)
    .filter((keyword) => keyword.length > 0)
    .map((keyword) => ({ keyword, lower: keyword.toLowerCase() }));
}

// 키워드를 찾을 글: 제목과 메시지를 소문자로 바꿔 '\n'으로 잇는다.
// 키워드에는 줄바꿈이 없으므로 제목과 메시지, 메시지와 메시지 경계에 걸쳐 맞지 않는다
export function keywordHaystackOf(conversation: ParsedConversation): string {
  const { title, messages } = lowerCasedTextOf(conversation);
  return [title, ...messages].join('\n');
}

// 글(keywordHaystackOf)에 들어 있는 키워드 (등록한 순서)
export function matchKeywords(
  haystack: string,
  targets: KeywordTarget[],
): string[] {
  return targets
    .filter((target) => haystack.includes(target.lower))
    .map((target) => target.keyword);
}

// 내 과목 키워드가 제목이나 메시지에 들어 있는 대화만 고른다 (대소문자 무시).
// 키워드가 하나도 없으면 아무것도 고르지 않는다: 공부와 상관없는 대화가 통째로 넘어가면 안 되기 때문이다.
export function filterByKeywords(
  conversations: ParsedConversation[],
  keywords: string[],
): KeywordMatch[] {
  const targets = keywordTargets(keywords);
  if (targets.length === 0) return [];

  const matches: KeywordMatch[] = [];
  for (const conversation of conversations) {
    const matchedKeywords = matchKeywords(
      keywordHaystackOf(conversation),
      targets,
    );
    if (matchedKeywords.length > 0)
      matches.push({ conversation, matchedKeywords });
  }
  return matches;
}
