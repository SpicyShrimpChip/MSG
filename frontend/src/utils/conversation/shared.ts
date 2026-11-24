import type { ParsedConversation } from './types';

// 대화 처리 파일 여러 곳에서 같이 쓰는 작은 함수. Web Worker에서도 읽으므로 다른 파일을 가져오지 않는다

// JSON에서 읽은 값이 (배열이 아닌) 객체인지
export function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

// 줄바꿈·여러 칸 공백을 한 칸으로 (앞뒤 글 한 줄 미리보기용)
export const oneLine = (text: string) => text.replace(/\s+/g, ' ');

// 최신 대화가 앞, 날짜 없는 대화는 맨 뒤 (Array.prototype.sort 비교 함수)
export const compareLatestFirst = (
  a: ParsedConversation,
  b: ParsedConversation,
) => (b.createdAt ?? '').localeCompare(a.createdAt ?? '');

interface LowerCasedText {
  title: string;
  messages: string[];
}

// 대화별 소문자 글. 대화 객체는 읽은 뒤 바꾸지 않으므로 한 번 만든 것을 다시 쓴다
// (고르기 화면은 체크·검색할 때마다 모든 대화를 훑는다)
const lowerCased = new WeakMap<ParsedConversation, LowerCasedText>();

// 제목과 메시지를 각각 소문자로 바꾼 글 (대소문자 무시 검색·키워드 찾기용)
export function lowerCasedTextOf(
  conversation: ParsedConversation,
): LowerCasedText {
  let cached = lowerCased.get(conversation);
  if (!cached) {
    cached = {
      title: conversation.title.toLowerCase(),
      messages: conversation.messages.map((message) =>
        message.text.toLowerCase(),
      ),
    };
    lowerCased.set(conversation, cached);
  }
  return cached;
}
