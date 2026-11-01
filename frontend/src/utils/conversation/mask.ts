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

interface Range {
  kind: MaskKind;
  start: number;
  end: number;
}

// 정규식으로 잡는 종류. 앞에 있을수록 겹칠 때 먼저 이긴다
// (예: 주민번호는 전화번호처럼 보일 수 있어서 주민번호가 먼저)
interface Rule {
  kind: MaskKind;
  pattern: RegExp;
  validate?: (matched: string) => boolean;
}

function isValidRrn(matched: string): boolean {
  const digits = matched.replace(/\D/g, '');
  if (digits.length !== 13) return false;
  const month = Number(digits.slice(2, 4));
  const day = Number(digits.slice(4, 6));
  return month >= 1 && month <= 12 && day >= 1 && day <= 31;
}

// 카드번호는 숫자 자릿수만으로는 오탐이 많아서 Luhn 검사를 통과한 것만 가린다
function passesLuhn(matched: string): boolean {
  const digits = matched.replace(/\D/g, '');
  if (digits.length < 13 || digits.length > 19) return false;
  let sum = 0;
  let double = false;
  for (let i = digits.length - 1; i >= 0; i -= 1) {
    let n = Number(digits[i]);
    if (double) {
      n *= 2;
      if (n > 9) n -= 9;
    }
    sum += n;
    double = !double;
  }
  return sum % 10 === 0;
}

const OCTET = '(?:25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)';

const RULES: Rule[] = [
  {
    kind: 'secret',
    // OpenAI·Anthropic 키, GitHub 토큰, AWS 액세스 키, Google API 키, Slack 토큰, JWT, Bearer 토큰
    pattern:
      /\b(?:sk-(?:ant-)?[A-Za-z0-9_-]{16,}|gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{30,}|AKIA[0-9A-Z]{16}|AIza[0-9A-Za-z_-]{35}|xox[baprs]-[A-Za-z0-9-]{10,}|eyJ[A-Za-z0-9_-]{8,}\.eyJ[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,})\b|Bearer\s+[A-Za-z0-9._~+/-]{20,}=*/g,
  },
  {
    kind: 'email',
    pattern:
      /[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}/g,
  },
  {
    kind: 'rrn',
    pattern: /(?<!\d)\d{6}[-\s]?[1-4]\d{6}(?!\d)/g,
    validate: isValidRrn,
  },
  {
    kind: 'card',
    pattern: /(?<!\d)\d(?:[ -]?\d){12,18}(?!\d)/g,
    validate: passesLuhn,
  },
  {
    kind: 'phone',
    // 휴대폰(010 등)·지역번호·070, 국가번호 +82 형태 포함
    pattern:
      /(?<![\d-])(?:\+82[-.\s]?0?|0)(?:10|1[16789]|2|[3-6][1-5]|70)[-.\s]?\d{3,4}[-.\s]?\d{4}(?![\d-])/g,
  },
  {
    kind: 'ip',
    // 버전 번호(1.2.3.4)처럼 생긴 것도 가려질 수 있다. 개인정보 쪽으로 치우친 선택
    pattern: new RegExp(`(?<![\\d.])(?:${OCTET}\\.){3}${OCTET}(?![\\d.])`, 'g'),
  },
];

// 사용자가 넣은 단어를 정규식에 그대로 넣을 수 있게 특수 문자를 이스케이프한다
export function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

// 이름·직접 추가한 단어: 글자 그대로 (대소문자 무시) 찾는다
function literalRanges(
  text: string,
  words: string[] | undefined,
  kind: MaskKind,
): Range[] {
  const ranges: Range[] = [];
  const unique = [
    ...new Set((words ?? []).map((w) => w.trim()).filter(Boolean)),
  ];
  // 긴 단어를 먼저 찾아야 "김주미"가 "주미"에 쪼개지지 않는다
  unique.sort((a, b) => b.length - a.length);
  for (const word of unique) {
    const pattern = new RegExp(escapeRegExp(word), 'gi');
    for (const found of text.matchAll(pattern)) {
      ranges.push({
        kind,
        start: found.index,
        end: found.index + found[0].length,
      });
    }
  }
  return ranges;
}

function overlaps(a: Range, b: Range): boolean {
  return a.start < b.end && b.start < a.end;
}

export function maskText(text: string, options: MaskOptions = {}): MaskResult {
  const candidates: Range[] = [];
  for (const rule of RULES) {
    for (const found of text.matchAll(rule.pattern)) {
      if (rule.validate && !rule.validate(found[0])) continue;
      candidates.push({
        kind: rule.kind,
        start: found.index,
        end: found.index + found[0].length,
      });
    }
  }
  candidates.push(
    ...literalRanges(text, options.names, 'name'),
    ...literalRanges(text, options.terms, 'custom'),
  );

  // candidates는 우선순위 순서로 쌓여 있다. 앞선 것과 겹치면 버린다
  const accepted: Range[] = [];
  for (const candidate of candidates) {
    if (!accepted.some((range) => overlaps(range, candidate))) {
      accepted.push(candidate);
    }
  }
  accepted.sort((a, b) => a.start - b.start);

  let result = '';
  let cursor = 0;
  const matches: MaskMatch[] = [];
  for (const range of accepted) {
    result += text.slice(cursor, range.start);
    const label = MASK_LABELS[range.kind];
    matches.push({
      kind: range.kind,
      original: text.slice(range.start, range.end),
      start: result.length,
      end: result.length + label.length,
    });
    result += label;
    cursor = range.end;
  }
  result += text.slice(cursor);
  return { text: result, matches };
}

export interface MaskedConversation {
  conversation: ParsedConversation;
  // 제목과 모든 메시지에서 가린 개수 (종류별)
  counts: Partial<Record<MaskKind, number>>;
  totalCount: number;
}

// 제목과 모든 메시지를 가린 새 대화를 만든다. 원래 대화는 바꾸지 않는다
export function maskConversation(
  conversation: ParsedConversation,
  options: MaskOptions = {},
): MaskedConversation {
  const counts: Partial<Record<MaskKind, number>> = {};
  let totalCount = 0;
  const run = (text: string): string => {
    const { text: masked, matches } = maskText(text, options);
    for (const match of matches) {
      counts[match.kind] = (counts[match.kind] ?? 0) + 1;
      totalCount += 1;
    }
    return masked;
  };

  const masked: ParsedConversation = {
    ...conversation,
    title: run(conversation.title),
    messages: conversation.messages.map((message) => ({
      ...message,
      text: run(message.text),
    })),
  };
  return { conversation: masked, counts, totalCount };
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

const oneLine = (text: string) => text.replace(/\s+/g, ' ');

// 가린 결과 글 안의 대체 표시(`[이메일]` 등)를 모두 찾는 정규식
const LABEL_PATTERN = new RegExp(
  Object.values(MASK_LABELS).map(escapeRegExp).join('|'),
  'g',
);

const countLabels = (text: string) => text.match(LABEL_PATTERN)?.length ?? 0;

// 대화의 제목과 모든 메시지에서 가린 곳을 순서대로 모은다 (메시지 위치와 상관없이 전부)
export function collectMaskedSpots(
  conversation: ParsedConversation,
  options: MaskOptions = {},
): MaskedSpot[] {
  const spots: MaskedSpot[] = [];
  const texts = [
    conversation.title,
    ...conversation.messages.map((m) => m.text),
  ];
  texts.forEach((text, i) => {
    const { text: masked, matches } = maskText(text, options);
    for (const match of matches) {
      spots.push({
        messageIndex: i - 1,
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
        labelIndex: countLabels(masked.slice(0, match.start)),
      });
    }
  });
  return spots;
}
