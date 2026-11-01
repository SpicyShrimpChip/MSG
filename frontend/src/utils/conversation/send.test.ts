import { describe, expect, it } from 'vitest';
import {
  MAX_MESSAGE_LENGTH,
  MAX_REQUEST_BYTES,
  MAX_TITLE_LENGTH,
  buildImportRequests,
  partCountOf,
  splitLongText,
  truncateTitle,
} from './send';
import type { ParsedConversation } from './types';

function conversation(id: string): ParsedConversation {
  return {
    id,
    source: 'chatgpt',
    title: `대화 ${id}`,
    createdAt: '2026-10-20T09:00:00.000Z',
    messages: [
      { role: 'user', text: '공개키로 암호화하면?' },
      { role: 'assistant', text: '[이메일]로 보내 드릴게요' },
    ],
  };
}

const IMPORT_ID = '0b7c2f0e-8d4a-4c55-9a51-2f7e3b9d1c00';

describe('buildImportRequests', () => {
  it('과목별로 묶고 고른 순서를 지킨다', () => {
    const requests = buildImportRequests(
      [
        { courseId: 2, conversation: conversation('a') },
        { courseId: 1, conversation: conversation('b') },
        { courseId: 2, conversation: conversation('c') },
      ],
      IMPORT_ID,
    );
    expect(requests.map((r) => r.courseId)).toEqual([2, 1]);
    expect(requests[0].conversations.map((c) => c.clientId)).toEqual([
      'a',
      'c',
    ]);
  });

  it('파일 안의 id를 clientId로 쓰고 서버가 받을 필드만 보낸다', () => {
    const [request] = buildImportRequests(
      [{ courseId: 1, conversation: conversation('a') }],
      IMPORT_ID,
    );
    expect(request.conversations[0]).toEqual({
      clientId: 'a',
      source: 'chatgpt',
      title: '대화 a',
      createdAt: '2026-10-20T09:00:00.000Z',
      messages: [
        { role: 'user', text: '공개키로 암호화하면?' },
        { role: 'assistant', text: '[이메일]로 보내 드릴게요' },
      ],
    });
  });

  it('한 요청의 최대 개수를 넘으면 나눠 보낸다', () => {
    const items = ['a', 'b', 'c', 'd', 'e'].map((id) => ({
      courseId: 1,
      conversation: conversation(id),
    }));
    const requests = buildImportRequests(items, IMPORT_ID, 2);
    expect(requests.map((r) => r.conversations.length)).toEqual([2, 2, 1]);
    expect(requests.every((r) => r.courseId === 1)).toBe(true);
  });

  // 한글 메시지 하나가 대략 300바이트가 되게 만든 긴 대화
  function longConversation(id: string, count: number): ParsedConversation {
    return {
      ...conversation(id),
      messages: Array.from({ length: count }, (_, i) => ({
        role: i % 2 === 0 ? ('user' as const) : ('assistant' as const),
        text: `${i + 1}번 메시지 ${'가'.repeat(90)}`,
      })),
    };
  }
  const bytesOf = (value: unknown) =>
    new TextEncoder().encode(JSON.stringify(value)).length;

  it('작은 대화는 나누지 않는다', () => {
    expect(partCountOf(conversation('a'))).toBe(1);
    const [request] = buildImportRequests(
      [{ courseId: 1, conversation: conversation('a') }],
      IMPORT_ID,
    );
    expect(request.conversations[0].clientId).toBe('a');
    expect(request.conversations[0]).not.toHaveProperty('part');
  });

  it('요청 크기를 넘는 대화는 메시지를 빠짐없이 순서대로 조각에 나눠 담는다', () => {
    const long = longConversation('long', 40); // 약 12KB
    const maxBytes = 4_000;
    const requests = buildImportRequests(
      [{ courseId: 1, conversation: long }],
      IMPORT_ID,
      50,
      maxBytes,
    );
    const pieces = requests.flatMap((r) => r.conversations);

    expect(pieces.length).toBeGreaterThan(1);
    expect(partCountOf(long, maxBytes)).toBe(pieces.length);
    expect(pieces.map((p) => p.clientId)).toEqual(
      pieces.map((_, i) => `long#${i + 1}`),
    );
    expect(pieces.map((p) => p.part)).toEqual(
      pieces.map((_, i) => ({
        parentClientId: 'long',
        index: i + 1,
        count: pieces.length,
      })),
    );
    expect(pieces.every((p) => p.title === '대화 long')).toBe(true);
    // 이어 붙이면 원래 메시지와 같다
    expect(pieces.flatMap((p) => p.messages)).toEqual(long.messages);
    // 요청 하나하나가 한도 안에 들어간다
    for (const request of requests) {
      expect(bytesOf(request)).toBeLessThanOrEqual(maxBytes);
    }
  });

  it('여러 대화를 묶을 때도 요청 크기 한도를 지킨다', () => {
    const items = ['a', 'b', 'c', 'd'].map((id) => ({
      courseId: 1,
      conversation: longConversation(id, 6), // 각각 약 2KB
    }));
    const maxBytes = 5_000;
    const requests = buildImportRequests(items, IMPORT_ID, 50, maxBytes);

    expect(requests.length).toBeGreaterThan(1);
    expect(
      requests.flatMap((r) => r.conversations.map((c) => c.clientId)),
    ).toEqual(['a', 'b', 'c', 'd']);
    for (const request of requests) {
      expect(bytesOf(request)).toBeLessThanOrEqual(maxBytes);
    }
  });

  it('모든 요청에 같은 작업 id와 번호·전체 요청 수를 붙인다', () => {
    const items = ['a', 'b', 'c'].map((id, i) => ({
      courseId: i === 2 ? 2 : 1,
      conversation: conversation(id),
    }));
    const requests = buildImportRequests(items, IMPORT_ID, 1);
    expect(
      requests.map(({ importId, requestIndex, requestCount, courseId }) => ({
        importId,
        requestIndex,
        requestCount,
        courseId,
      })),
    ).toEqual([
      { importId: IMPORT_ID, requestIndex: 1, requestCount: 3, courseId: 1 },
      { importId: IMPORT_ID, requestIndex: 2, requestCount: 3, courseId: 1 },
      { importId: IMPORT_ID, requestIndex: 3, requestCount: 3, courseId: 2 },
    ]);
    // 같은 대화로 다시 만들면 같은 요청이 나온다 (다시 보내기에서 서버가 중복을 알아본다)
    expect(buildImportRequests(items, IMPORT_ID, 1)).toEqual(requests);
  });

  it('20,000자가 넘는 메시지는 이어짐 표시를 붙여 나누고 글자를 버리지 않는다', () => {
    const text = '가'.repeat(MAX_MESSAGE_LENGTH * 2 + 10);
    const [request] = buildImportRequests(
      [
        {
          courseId: 1,
          conversation: {
            ...conversation('a'),
            messages: [
              { role: 'user', text },
              { role: 'assistant', text: '답' },
            ],
          },
        },
      ],
      IMPORT_ID,
    );
    const { messages } = request.conversations[0];
    expect(messages.map((m) => m.text.length)).toEqual([
      MAX_MESSAGE_LENGTH,
      MAX_MESSAGE_LENGTH,
      10,
      1,
    ]);
    expect(messages.map((m) => m.continued)).toEqual([
      true,
      true,
      undefined,
      undefined,
    ]);
    expect(messages.map((m) => m.role)).toEqual([
      'user',
      'user',
      'user',
      'assistant',
    ]);
    expect(
      messages
        .slice(0, 3)
        .map((m) => m.text)
        .join(''),
    ).toBe(text);
  });

  it('최대 길이 메시지로 가득 찬 대화도 요청 하나가 한도를 넘지 않는다', () => {
    // 4바이트 글자(이모지)만 있는 메시지: 20,000 단위 = 10,000글자 = 40KB
    const emojiText = '😀'.repeat(MAX_MESSAGE_LENGTH / 2);
    const long: ParsedConversation = {
      ...conversation('emoji'),
      messages: Array.from({ length: 60 }, () => ({
        role: 'user' as const,
        text: emojiText,
      })),
    };
    const requests = buildImportRequests(
      [{ courseId: 1, conversation: long }],
      IMPORT_ID,
    );
    expect(requests.length).toBeGreaterThan(1);
    for (const request of requests) {
      expect(bytesOf(request)).toBeLessThanOrEqual(MAX_REQUEST_BYTES);
      for (const message of request.conversations.flatMap((c) => c.messages))
        expect(message.text.length).toBeLessThanOrEqual(MAX_MESSAGE_LENGTH);
    }
  });

  it('500자가 넘는 제목은 잘라 보낸다', () => {
    const [request] = buildImportRequests(
      [
        {
          courseId: 1,
          conversation: { ...conversation('a'), title: '가'.repeat(600) },
        },
      ],
      IMPORT_ID,
    );
    expect(request.conversations[0].title).toHaveLength(MAX_TITLE_LENGTH);
  });

  it('고른 대화가 없으면 요청도 없다', () => {
    expect(buildImportRequests([], IMPORT_ID)).toEqual([]);
  });
});

describe('splitLongText', () => {
  it('짧은 글은 그대로 둔다', () => {
    expect(splitLongText('가나다', 3)).toEqual(['가나다']);
  });

  it('두 단위로 된 글자(이모지)를 가르지 않는다', () => {
    const pieces = splitLongText('a😀b', 2);
    expect(pieces).toEqual(['a', '😀', 'b']);
    expect(pieces.join('')).toBe('a😀b');
  });
});

describe('truncateTitle', () => {
  it('이모지가 걸쳐 있으면 그 글자 앞에서 자른다', () => {
    const title = '가'.repeat(MAX_TITLE_LENGTH - 1) + '😀';
    expect(truncateTitle(title)).toBe('가'.repeat(MAX_TITLE_LENGTH - 1));
  });
});
