import type {
  ConversationImport,
  ImportedConversation,
  ImportedMessage,
} from '@/types';
import type { ConversationMessage, ParsedConversation } from './types';

// 한 번에 보내는 대화 수. 서버 한도(ConversationImportRequest.MAX_CONVERSATIONS)와 같은 값
export const MAX_CONVERSATIONS_PER_REQUEST = 50;
// 요청 한 번의 최대 크기(바이트). 서버 한도는 2MB(app.conversation-import.max-request-size)이고,
// 배포 때 거치는 nginx 기본 한도(1MB)에 걸리지 않게 1MB로 나눠 보낸다
export const MAX_REQUEST_BYTES = 1_000_000;
// 메시지 하나·제목의 최대 글자 수. 서버 한도(ConversationMessage.CONTENT_MAX_LENGTH, Conversation.TITLE_MAX_LENGTH)와 같은 값.
// 글자 수는 서버(Java)와 같은 UTF-16 단위(string.length)로 센다
export const MAX_MESSAGE_LENGTH = 20_000;
export const MAX_TITLE_LENGTH = 500;

// 요청 본문에서 대화 목록 밖의 글자({"importId":..,"requestIndex":..,"requestCount":..,"courseId":..,"conversations":[]})와
// 조각 표시(clientId 뒤 "#번호", part 필드)가 늘리는 크기를 넉넉히 잡은 값
const ENVELOPE_BYTES = 400;

export interface ConversationToSend {
  courseId: number;
  // 개인정보를 가린 뒤의 대화
  conversation: ParsedConversation;
}

const encoder = new TextEncoder();
const byteLength = (value: unknown) =>
  encoder.encode(JSON.stringify(value)).length;

// text를 end 앞에서 자를 위치. 이모지처럼 두 단위로 된 글자가 둘로 갈라지지 않게 한 칸 앞에서 자른다
function cutIndex(text: string, start: number, end: number): number {
  if (end >= text.length) return text.length;
  const code = text.charCodeAt(end - 1);
  const isHighSurrogate = code >= 0xd800 && code <= 0xdbff;
  return isHighSurrogate && end - 1 > start ? end - 1 : end;
}

// 긴 글을 max 글자씩 나눈다. 글자는 하나도 버리지 않는다
export function splitLongText(
  text: string,
  max = MAX_MESSAGE_LENGTH,
): string[] {
  if (text.length <= max) return [text];
  const pieces: string[] = [];
  let start = 0;
  while (start < text.length) {
    const end = cutIndex(text, start, start + max);
    pieces.push(text.slice(start, end));
    start = end;
  }
  return pieces;
}

// 20,000자가 넘는 메시지는 같은 역할의 메시지 여러 개로 나누고, 마지막이 아닌 조각에 continued를 붙인다
// (서버는 이어진 글로 저장하고, 개인정보 검사도 이어 붙인 글로 한다)
function toImportedMessages(
  messages: ConversationMessage[],
): ImportedMessage[] {
  return messages.flatMap(({ role, text }) => {
    const pieces = splitLongText(text);
    return pieces.map((piece, i) =>
      i < pieces.length - 1
        ? { role, text: piece, continued: true }
        : { role, text: piece },
    );
  });
}

// 제목은 보조 정보라 500자를 넘으면 잘라 보낸다
export function truncateTitle(title: string): string {
  return title.slice(0, cutIndex(title, 0, MAX_TITLE_LENGTH));
}

// 대화 하나를 서버가 받을 모양으로 바꾼다. 요청 한 번에 들어가지 않을 만큼 크면
// 메시지를 앞에서부터 차례로 담아 조각 여러 개로 만든다. 메시지는 하나도 버리지 않는다.
// 메시지는 20,000자 이하로 나눈 뒤라 하나가 혼자서 요청 한도(1MB)를 넘지 않는다
export function toImportedConversations(
  { id, source, title, createdAt, messages }: ParsedConversation,
  maxBytes = MAX_REQUEST_BYTES,
): ImportedConversation[] {
  const whole: ImportedConversation = {
    clientId: id,
    source,
    title: truncateTitle(title),
    createdAt,
    messages: toImportedMessages(messages),
  };
  const budget = maxBytes - ENVELOPE_BYTES;
  if (byteLength(whole) <= budget) return [whole];

  const emptySize = byteLength({ ...whole, messages: [] });
  const groups: ImportedConversation['messages'][] = [];
  let current: ImportedConversation['messages'] = [];
  let size = emptySize;
  for (const message of whole.messages) {
    const bytes = byteLength(message);
    // 메시지 사이 쉼표 1바이트
    if (current.length > 0 && size + bytes + 1 > budget) {
      groups.push(current);
      current = [];
      size = emptySize;
    }
    size += bytes + (current.length > 0 ? 1 : 0);
    current.push(message);
  }
  groups.push(current);

  const count = groups.length;
  return groups.map((group, i) => ({
    ...whole,
    clientId: `${id}#${i + 1}`,
    messages: group,
    part: { parentClientId: id, index: i + 1, count },
  }));
}

// 나눠 보낼 때 몇 조각이 되는지 (보내기 화면에 표시한다)
export function partCountOf(
  conversation: ParsedConversation,
  maxBytes = MAX_REQUEST_BYTES,
): number {
  return toImportedConversations(conversation, maxBytes).length;
}

// 고른 대화를 과목별 요청으로 묶는다. 요청 하나는 과목 하나이고, 대화 수나 크기가 한도를 넘으면
// 여러 요청으로 나눈다. 과목 순서와 과목 안의 대화 순서는 고른 순서를 따른다. 나눈 조각도 한 개로 센다.
// 모든 요청에 같은 importId와 번호(1부터)·전체 요청 수를 붙인다. 같은 대화로 다시 만들면 같은 요청들이 나온다
export function buildImportRequests(
  items: ConversationToSend[],
  importId: string,
  maxPerRequest = MAX_CONVERSATIONS_PER_REQUEST,
  maxBytes = MAX_REQUEST_BYTES,
): ConversationImport[] {
  const byCourse = new Map<number, ImportedConversation[]>();
  for (const { courseId, conversation } of items) {
    const list = byCourse.get(courseId) ?? [];
    list.push(...toImportedConversations(conversation, maxBytes));
    byCourse.set(courseId, list);
  }

  const budget = maxBytes - ENVELOPE_BYTES;
  const batches: Pick<ConversationImport, 'courseId' | 'conversations'>[] = [];
  for (const [courseId, conversations] of byCourse) {
    let current: ImportedConversation[] = [];
    let size = 0;
    for (const conversation of conversations) {
      const bytes = byteLength(conversation);
      if (
        current.length > 0 &&
        (current.length >= maxPerRequest || size + bytes + 1 > budget)
      ) {
        batches.push({ courseId, conversations: current });
        current = [];
        size = 0;
      }
      size += bytes + (current.length > 0 ? 1 : 0);
      current.push(conversation);
    }
    if (current.length > 0) batches.push({ courseId, conversations: current });
  }
  return batches.map((batch, i) => ({
    importId,
    requestIndex: i + 1,
    requestCount: batches.length,
    ...batch,
  }));
}
