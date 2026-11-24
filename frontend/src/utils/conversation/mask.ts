import { coreMaskTexts, loadConversationCore } from './core';
import { oneLine } from './shared';
import type { ParsedConversation } from './types';

export type MaskKind =
  'secret' | 'email' | 'rrn' | 'card' | 'phone' | 'ip' | 'name' | 'custom';

export const MASK_LABELS: Record<MaskKind, string> = {
  secret: '[키·토큰]',
  email: '[이메일]',
  rrn: '[주민등록번호]',
  card: '[카드번호]',
  phone: '[전화번호]',
  ip: '[IP]',
  name: '[이름]',
  custom: '[가린 단어]',
};

export interface MaskOptions {
  // 내 이름 (이름이 여러 가지로 쓰이면 모두)
  names?: string[];
  // 사용자가 직접 추가한 가릴 단어
  terms?: string[];
}

export interface MaskMatch {
  kind: MaskKind;
  // 가리기 전의 글자. 미리보기 화면에서만 쓰고 서버로 보내지 않는다
  original: string;
  // 가린 결과 글 안에서 대체 표시가 있는 자리
  start: number;
  end: number;
}

export interface MaskResult {
  text: string;
  matches: MaskMatch[];
}

// 글자를 정규식에 그대로 넣을 수 있게 특수 문자를 이스케이프한다 (가린 표시·키워드 칠하기용. 가리기 자체는 러스트 WASM이 한다)
export function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

// 가리는 규칙(이메일·전화번호·주민번호·카드번호·키·IP, 이름·직접 추가한 단어)은 러스트 WebAssembly에 있다
// (frontend/wasm/conversation-core/src/mask.rs, 서버 PersonalInfoDetector와 같은 규칙).
// 겹치면 앞선 규칙이 이기고, 이름·단어는 긴 것부터 대소문자 무시로 찾는다. 위치는 JS 문자열(UTF-16) 기준이다
function maskTexts(texts: string[], options: MaskOptions): MaskResult[] {
  return coreMaskTexts(texts, options.names ?? [], options.terms ?? []).map(
    (result) => ({
      text: result.text,
      matches: result.matches.map((match) => ({
        ...match,
        kind: match.kind as MaskKind,
      })),
    }),
  );
}

export function maskText(text: string, options: MaskOptions = {}): MaskResult {
  return maskTexts([text], options)[0];
}

export interface MaskedConversation {
  conversation: ParsedConversation;
  // 제목과 모든 메시지에서 가린 개수 (종류별)
  counts: Partial<Record<MaskKind, number>>;
  totalCount: number;
}

// 가린 곳 하나 (미리보기 화면의 "가린 곳 목록"용). original은 화면에서만 쓰고 서버로 보내지 않는다
export interface MaskedSpot {
  // 제목이면 -1, 아니면 메시지 순서
  messageIndex: number;
  kind: MaskKind;
  original: string;
  // 가린 결과 글에서 표시 앞뒤 글 (줄바꿈은 한 칸으로)
  before: string;
  after: string;
  // 이 가린 표시가 그 글(제목 또는 메시지) 안의 가린 표시(`[IP]` 등) 중 몇 번째인지, 0부터.
  // 사용자가 직접 쓴 `[IP]` 같은 글자도 세므로 화면의 글자 순서와 맞는다
  labelIndex: number;
}

// 앞뒤로 보여 줄 글자 수
const SPOT_CONTEXT_LENGTH = 20;

// 가린 결과 글 안의 대체 표시(`[이메일]` 등)를 모두 찾는 정규식 (g 플래그라 `match`·`matchAll`로만 쓴다.
// 둘은 lastIndex를 남기지 않아 여러 곳에서 같이 써도 된다)
export const MASK_LABEL_PATTERN = new RegExp(
  Object.values(MASK_LABELS).map(escapeRegExp).join('|'),
  'g',
);

const countLabels = (text: string) =>
  text.match(MASK_LABEL_PATTERN)?.length ?? 0;

// 가린 결과 글 하나에서 가린 곳 목록을 만든다 (messageIndex: 제목이면 -1).
// 가린 표시 순서(labelIndex)는 앞 가린 곳부터 이어서 센다 (가린 곳마다 글 앞부분을 처음부터 세면 가린 곳이 많을 때 느리다).
// 가린 표시는 '['로만 시작하므로 가린 곳 시작 위치에서 나눠 세도 걸쳐 있는 표시가 없다
function spotsOf(
  masked: string,
  matches: MaskMatch[],
  messageIndex: number,
): MaskedSpot[] {
  let labelCount = 0;
  let counted = 0;
  return matches.map((match) => {
    labelCount += countLabels(masked.slice(counted, match.start));
    counted = match.start;
    return {
      messageIndex,
      kind: match.kind,
      original: match.original,
      before: oneLine(
        masked.slice(
          Math.max(0, match.start - SPOT_CONTEXT_LENGTH),
          match.start,
        ),
      ).trimStart(),
      after: oneLine(
        masked.slice(match.end, match.end + SPOT_CONTEXT_LENGTH),
      ).trimEnd(),
      labelIndex: labelCount,
    };
  });
}

export interface MaskedConversationWithSpots extends MaskedConversation {
  // 가린 곳 목록 (미리 보기 화면용). original이 들어 있어 서버로 보내지 않는다
  spots: MaskedSpot[];
}

// 제목과 모든 메시지를 가린 새 대화와 가린 곳 목록을 한 번에 만든다. 원래 대화는 바꾸지 않는다
// (Web Worker에서 대화 여러 개를 한꺼번에 처리할 때 쓴다)
export function maskConversationWithSpots(
  conversation: ParsedConversation,
  options: MaskOptions = {},
): MaskedConversationWithSpots {
  const counts: Partial<Record<MaskKind, number>> = {};
  const spots: MaskedSpot[] = [];
  let totalCount = 0;
  // 제목과 메시지를 한 번에 넘겨 가릴 단어 정규식을 한 번만 만든다
  const results = maskTexts(
    [conversation.title, ...conversation.messages.map((m) => m.text)],
    options,
  );
  results.forEach(({ text, matches }, index) => {
    for (const match of matches) {
      counts[match.kind] = (counts[match.kind] ?? 0) + 1;
      totalCount += 1;
    }
    spots.push(...spotsOf(text, matches, index - 1));
  });
  const masked: ParsedConversation = {
    ...conversation,
    title: results[0].text,
    messages: conversation.messages.map((message, index) => ({
      ...message,
      text: results[index + 1].text,
    })),
  };
  return { conversation: masked, counts, totalCount, spots };
}

// 대화 여러 개를 한꺼번에 가린다. 가리기 규칙(러스트 WebAssembly)을 먼저 불러온다.
// Web Worker와, Worker를 쓸 수 없을 때 화면 스레드가 같이 쓴다. 화면은 worker/client.ts의 maskConversationsOffThread를 부른다
export async function maskConversations(
  conversations: ParsedConversation[],
  options: MaskOptions,
): Promise<MaskedConversationWithSpots[]> {
  await loadConversationCore();
  return conversations.map((conversation) =>
    maskConversationWithSpots(conversation, options),
  );
}
