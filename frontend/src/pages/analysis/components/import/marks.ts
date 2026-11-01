import { MASK_LABELS, escapeRegExp } from '@/utils';

// 대화 보기에서 가린 표시(파랑)와 찾은 키워드·검색어(형광펜)를 찾아 칠하는 부분

// 가린 표시는 파랑, 찾은 키워드·검색어는 형광펜(노랑)
export const MARK_CLASS = {
  mask: 'rounded bg-primary-soft px-0.5 font-medium text-heading',
  keyword: 'rounded bg-highlight px-0.5 text-on-highlight',
} as const;

const MASK_SET = new Set<string>(Object.values(MASK_LABELS));

// 가린 표시와 키워드를 한 번에 찾는 정규식. 긴 단어를 먼저 찾는다
export function markPattern(keywords: string[]): RegExp {
  const words = [...MASK_SET, ...keywords]
    .sort((a, b) => b.length - a.length)
    .map(escapeRegExp);
  return new RegExp(`(${words.join('|')})`, 'gi');
}

export type MarkKind = keyof typeof MARK_CLASS;

// 글을 조각으로 나눈다. 홀수 번째가 찾은 것 (가린 표시면 mask, 아니면 keyword)
export function splitMarks(
  text: string,
  pattern: RegExp,
): { text: string; kind?: MarkKind }[] {
  return text
    .split(pattern)
    .map((part, i) =>
      i % 2 === 0
        ? { text: part }
        : { text: part, kind: MASK_SET.has(part) ? 'mask' : 'keyword' },
    );
}

// 화면에 그려진 글 안에서 가린 표시(`[IP]` 등)의 index번째 자리를 찾는다 (코드 블록 안도 센다).
// 가린 표시가 글 조각 하나에 통째로 들어 있어서 조각마다 찾아 순서대로 센다
export function findMaskLabelRange(root: Node, index: number): Range | null {
  const labels = new RegExp([...MASK_SET].map(escapeRegExp).join('|'), 'g');
  const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
  let count = 0;
  for (let node = walker.nextNode(); node; node = walker.nextNode()) {
    for (const found of (node.nodeValue ?? '').matchAll(labels)) {
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
export function remarkMarks({ pattern }: { pattern: RegExp }) {
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
