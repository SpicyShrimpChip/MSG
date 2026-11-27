import { normalizeKeyword } from '../keywords';
import { escapeRegExp } from './mask';
import { lowerCasedTextOf } from './shared';
import type { ParsedConversation } from './types';

// 학습 키워드를 찾는 규칙 (백엔드 global/common/TermMatch와 같다. 한쪽을 고치면 다른 쪽도 같이 고친다.
// 다르면 여기서 고른 대화가 서버 분석 때 "키워드가 없다"로 빠진다). 대소문자는 무시한다.
// - 영문 키워드: ASCII 영문·숫자로 시작하고 ASCII 영문·숫자와 공백·. / + # & - 로만 된 키워드(RSA, VLAN, TCP/IP, C++).
//   낱말 경계에서만 찾는다: 찾은 곳 바로 앞뒤 글자가 ASCII 영문·숫자가 아니어야 한다(글 처음·끝, 공백, 기호, 한글은 경계).
//   그래서 "ship"의 "ip"는 안 걸리고 "IP를"·"TCP/IP"의 "IP"는 걸린다
// - 그 밖(한글 등이 든 키워드): 조사가 붙으므로 부분 문자열로 찾는다
const LATIN_KEYWORD = /^[A-Za-z0-9][A-Za-z0-9 ./+#&-]*$/;

export function isLatinKeyword(keyword: string): boolean {
  return LATIN_KEYWORD.test(keyword);
}

// ASCII 영문·숫자 한 글자인지 (낱말 경계 판단, 글 밖(undefined)은 경계)
export function isAsciiAlnum(char: string | undefined): boolean {
  return char !== undefined && /^[A-Za-z0-9]$/.test(char);
}

// 비교할 키워드: 등록한 모양(찾은 키워드로 보여 줌)과 소문자, 영문 키워드면 낱말 경계 정규식
// (한 번만 만들어 두고 대화마다 같이 쓴다)
export interface KeywordTarget {
  keyword: string;
  lower: string;
  // 소문자 글에 쓴다. 앞 경계는 앞 글자 확인(lookbehind) 대신 "글 처음이거나 영문·숫자가 아닌 글자"로 쓴다.
  // 한 글자씩만 보는 모양이라 긴 글에서도 느려지지 않는다. 한글 등이 든 키워드는 null(부분 문자열)
  boundary: RegExp | null;
}

// 키워드의 공백을 정리하고 빈 것은 뺀다
export function keywordTargets(keywords: string[]): KeywordTarget[] {
  return keywords
    .map(normalizeKeyword)
    .filter((keyword) => keyword.length > 0)
    .map((keyword) => {
      const lower = keyword.toLowerCase();
      return {
        keyword,
        lower,
        boundary: isLatinKeyword(keyword)
          ? new RegExp(`(?:^|[^a-z0-9])${escapeRegExp(lower)}(?![a-z0-9])`)
          : null,
      };
    });
}

// 소문자로 바꾼 글에 키워드가 있는지
export function containsKeyword(
  lowerText: string,
  target: KeywordTarget,
): boolean {
  return target.boundary
    ? target.boundary.test(lowerText)
    : lowerText.includes(target.lower);
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
    .filter((target) => containsKeyword(haystack, target))
    .map((target) => target.keyword);
}
