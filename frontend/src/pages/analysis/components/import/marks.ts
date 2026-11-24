import {
  MASK_LABELS,
  MASK_LABEL_PATTERN,
  escapeRegExp,
  isAsciiAlnum,
  isLatinKeyword,
  normalizeKeyword,
} from '@/utils';

// 대화 보기에서 가린 표시(파랑)와 찾은 키워드·검색어(형광펜)를 찾아 칠하는 부분

// 가린 표시는 파랑, 찾은 키워드·검색어는 형광펜(노랑)
export const MARK_CLASS = {
  mask: 'rounded bg-primary-soft px-0.5 font-medium text-heading',
  keyword: 'rounded bg-highlight px-0.5 text-on-highlight',
} as const;

const MASK_SET = new Set<string>(Object.values(MASK_LABELS));

// 칠할 단어 하나. 영문 학습 키워드(latin)는 대화를 고른 규칙(utils/conversation/filter.ts)과 같이 낱말 경계에서만 칠한다:
// 뒤 경계는 정규식 뒤 확인으로, 앞 경계는 찾은 뒤 코드로 본다(앞 글자 확인 정규식을 쓰지 않는다)
interface MarkWord {
  regex: RegExp;
  latin: boolean;
}

export type MarkPattern = MarkWord[];

// 칠할 단어들. keywords는 학습 키워드(영문이면 낱말 경계), phrases는 검색어처럼 부분 문자열로 찾은 글.
// 가린 표시도 함께 찾는다. 같은 자리에서 겹치면 긴 단어가 이긴다
export function markPattern(
  keywords: string[],
  phrases: string[] = [],
): MarkPattern {
  const plain = (word: string): MarkWord => ({
    regex: new RegExp(escapeRegExp(word), 'gi'),
    latin: false,
  });
  return [
    ...[...MASK_SET].map(plain),
    ...keywords
      .map(normalizeKeyword)
      .filter(Boolean)
      .map((word) =>
        isLatinKeyword(word)
          ? {
              regex: new RegExp(`${escapeRegExp(word)}(?![A-Za-z0-9])`, 'gi'),
              latin: true,
            }
          : plain(word),
      ),
    ...phrases
      .map((word) => word.trim())
      .filter(Boolean)
      .map(plain),
  ];
}

export type MarkKind = keyof typeof MARK_CLASS;

// 글을 조각으로 나눈다. 찾은 조각에는 kind가 붙는다 (가린 표시면 mask, 아니면 keyword).
// 앞에서부터, 같은 자리면 긴 것을 고르고 앞에서 고른 것과 겹치는 것은 버린다
export function splitMarks(
  text: string,
  pattern: MarkPattern,
): { text: string; kind?: MarkKind }[] {
  const found: { start: number; end: number }[] = [];
  for (const { regex, latin } of pattern) {
    regex.lastIndex = 0;
    for (let m = regex.exec(text); m; m = regex.exec(text)) {
      // 앞 글자가 영문·숫자면 낱말 가운데다: 한 글자 뒤부터 다시 찾는다
      if (latin && isAsciiAlnum(text[m.index - 1])) {
        regex.lastIndex = m.index + 1;
        continue;
      }
      found.push({ start: m.index, end: m.index + m[0].length });
    }
  }
  found.sort((a, b) => a.start - b.start || b.end - a.end);

  const parts: { text: string; kind?: MarkKind }[] = [];
  let last = 0;
  for (const { start, end } of found) {
    if (start < last) continue;
    if (start > last) parts.push({ text: text.slice(last, start) });
    const word = text.slice(start, end);
    parts.push({ text: word, kind: MASK_SET.has(word) ? 'mask' : 'keyword' });
    last = end;
  }
  if (last < text.length) parts.push({ text: text.slice(last) });
  return parts;
}

// 화면에 그려진 글 안에서 가린 표시(`[IP]` 등)의 index번째 자리를 찾는다 (코드 블록 안도 센다).
// 가린 표시가 글 조각 하나에 통째로 들어 있어서 조각마다 찾아 순서대로 센다
export function findMaskLabelRange(root: Node, index: number): Range | null {
  const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
  let count = 0;
  for (let node = walker.nextNode(); node; node = walker.nextNode()) {
    for (const found of (node.nodeValue ?? '').matchAll(MASK_LABEL_PATTERN)) {
      if (count === index) {
        const range = document.createRange();
        range.setStart(node, found.index);
        range.setEnd(node, found.index + found[0].length);
        return range;
      }
      count += 1;
    }
  }
  return null;
}

// 마크다운 트리에서 쓰는 만큼만 적은 모양
interface MdNode {
  type: string;
  value?: string;
  children?: MdNode[];
  data?: { hName?: string; hProperties?: Record<string, string> };
}

// 마크다운 글 조각 안의 가린 표시·키워드를 <mark>로 바꾸는 remark 플러그인 (코드 블록 안은 그대로 둔다)
export function remarkMarks({ pattern }: { pattern: MarkPattern }) {
  const visit = (node: MdNode) => {
    if (!node.children) return;
    node.children = node.children.flatMap((child): MdNode[] => {
      if (child.type !== 'text' || !child.value) {
        visit(child);
        return [child];
      }
      return splitMarks(child.value, pattern)
        .filter((part) => part.text)
        .map((part): MdNode =>
          part.kind
            ? {
                type: 'mark',
                data: {
                  hName: 'mark',
                  hProperties: { className: MARK_CLASS[part.kind] },
                },
                children: [{ type: 'text', value: part.text }],
              }
            : { type: 'text', value: part.text },
        );
    });
  };
  return (tree: MdNode) => visit(tree);
}
