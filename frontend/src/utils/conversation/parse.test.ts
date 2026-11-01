import { describe, expect, it } from 'vitest';
import { filterByKeywords } from './filter';
import { parseConversationExport } from './parse';

// 실제 내보내기 파일이 아니라 알려진 구조를 흉내 낸 가짜 데이터다.
// Claude는 2026-10-31, ChatGPT는 2026-11-01 실제 내보내기 파일로 확인한 구조를 따른다(실제 기록은 레포에 넣지 않는다)

function chatGptFile() {
  return JSON.stringify([
    {
      conversation_id: 'gpt-1',
      title: 'RSA 암호 이해',
      create_time: 1760000000,
      current_node: 'm4',
      mapping: {
        root: { parent: null, children: ['sys'], message: null },
        sys: {
          parent: 'root',
          children: ['m1'],
          message: {
            author: { role: 'system' },
            content: { content_type: 'text', parts: [''] },
          },
        },
        m1: {
          parent: 'sys',
          children: ['m2', 'm2b'],
          message: {
            author: { role: 'user' },
            content: { content_type: 'text', parts: ['RSA가 이해가 안 돼요'] },
          },
        },
        // 다시 생성하기로 갈라진 예전 가지
        m2b: {
          parent: 'm1',
          children: [],
          message: {
            author: { role: 'assistant' },
            content: { content_type: 'text', parts: ['예전 답변'] },
          },
        },
        m2: {
          parent: 'm1',
          children: ['m3'],
          message: {
            author: { role: 'assistant' },
            content: {
              content_type: 'text',
              parts: ['소인수분해가 어려운 점을 이용해요'],
            },
          },
        },
        m3: {
          parent: 'm2',
          children: ['m4'],
          message: {
            author: { role: 'user' },
            content: {
              content_type: 'multimodal_text',
              parts: [
                {
                  content_type: 'image_asset_pointer',
                  asset_pointer: 'file-service://x',
                },
                '이 그림은 뭐예요?',
              ],
            },
          },
        },
        m4: {
          parent: 'm3',
          children: [],
          message: {
            author: { role: 'assistant' },
            content: {
              content_type: 'text',
              parts: ['공개키 암호 그림이에요'],
            },
          },
        },
      },
    },
    // 메시지가 없는 대화
    {
      conversation_id: 'gpt-empty',
      title: '빈 대화',
      mapping: { root: { parent: null, children: [], message: null } },
    },
  ]);
}

function claudeFile() {
  return JSON.stringify([
    {
      uuid: 'cl-1',
      name: '운영체제 교착 상태',
      created_at: '2026-10-20T10:00:00.000000Z',
      chat_messages: [
        {
          uuid: 'a',
          sender: 'human',
          text: '교착 상태가 뭐예요?',
          content: [{ type: 'text', text: '교착 상태가 뭐예요?' }],
          files: [{ file_name: 'screen.png' }],
          attachments: [
            { file_name: 'note.txt', extracted_content: '개인 노트 내용' },
          ],
        },
        {
          uuid: 'b',
          sender: 'assistant',
          text: '',
          content: [
            { type: 'thinking', thinking: '생각 중' },
            { type: 'text', text: '서로 자원을 기다리며 멈추는 상태예요.' },
          ],
        },
      ],
    },
    { uuid: 'cl-empty', name: '', chat_messages: [] },
  ]);
}

// ChatGPT 대화 하나: 메시지를 순서대로 한 가지로 잇는다
function chatGptConversation(id: string, messages: object[]) {
  const mapping: Record<string, object> = {
    root: { parent: null, children: ['n0'], message: null },
  };
  messages.forEach((message, index) => {
    mapping[`n${index}`] = {
      parent: index === 0 ? 'root' : `n${index - 1}`,
      children: index === messages.length - 1 ? [] : [`n${index + 1}`],
      message,
    };
  });
  return {
    conversation_id: id,
    title: id,
    create_time: 1760000000,
    current_node: `n${messages.length - 1}`,
    mapping,
  };
}

function gptMessage(
  role: string,
  parts: unknown[],
  extra: { contentType?: string; attachments?: object[] } = {},
) {
  return {
    author: { role },
    content: { content_type: extra.contentType ?? 'text', parts },
    metadata: extra.attachments ? { attachments: extra.attachments } : {},
  };
}

function gptTexts(messages: object[]) {
  const result = parseConversationExport(
    'conversations-000.json',
    JSON.stringify([chatGptConversation('gpt', messages)]),
  );
  if (!result.ok) throw new Error(result.message);
  return result.conversations[0].messages.map((m) => [m.role, m.text]);
}

// ChatGPT 답변 속 인용·위젯 표시: 종류인자
const mark = (kind: string, ...args: string[]) => `${kind}${args.join('')}`;

function claudeConversation(uuid: string, chatMessages: object[]) {
  return {
    uuid,
    name: '',
    created_at: '2026-10-30T10:00:00.000000Z',
    chat_messages: chatMessages,
  };
}

describe('parseConversationExport', () => {
  it('ChatGPT 파일에서 현재 가지의 대화만 순서대로 뽑는다', () => {
    const result = parseConversationExport('conversations.json', chatGptFile());
    if (!result.ok) throw new Error(result.message);
    expect(result.source).toBe('chatgpt');
    expect(result.skippedCount).toBe(1);
    expect(result.conversations).toHaveLength(1);

    const [conversation] = result.conversations;
    expect(conversation).toMatchObject({
      id: 'gpt-1',
      title: 'RSA 암호 이해',
      createdAt: '2025-10-09T08:53:20.000Z',
    });
    expect(conversation.messages.map((m) => [m.role, m.text])).toEqual([
      ['user', 'RSA가 이해가 안 돼요'],
      ['assistant', '소인수분해가 어려운 점을 이용해요'],
      ['user', '[이미지]\n이 그림은 뭐예요?'],
      ['assistant', '공개키 암호 그림이에요'],
    ]);
  });

  it('ChatGPT 답변의 출처·위젯 표시는 지우고 이름·링크 글자는 남긴다', () => {
    const answer = [
      `RSA는 소인수분해가 어렵다는 점을 써요 ${mark('cite', 'turn0search1', 'turn0search3')}.`,
      `${mark('entity', JSON.stringify(['people', '론 리베스트', '암호학자']))}가 만들었어요.`,
      `자세한 건 ${mark('url', 'RSA 위키', 'https://example.com/rsa')}를 보세요 ${mark('filecite', 'turn1file0', 'L1-L9')}`,
      `${mark('link_title', '표준 문서', 'turn2search0')} 참고`,
      `${mark('genui', '{"type":"chart"}')}`,
      `${mark('image_group', '{"query":["rsa"]}')}끝${mark('message_reaction', '👍')}`,
      // 짝이 맞지 않고 남은 표시 문자
      '남은 표시',
    ].join('\n');

    expect(
      gptTexts([
        gptMessage('user', [`RSA가 뭐예요? ${mark('cite', 'turn0search1')}`]),
        gptMessage('assistant', [answer]),
      ]),
    ).toEqual([
      // 사용자가 쓴 글은 건드리지 않는다
      ['user', `RSA가 뭐예요? ${mark('cite', 'turn0search1')}`],
      [
        'assistant',
        [
          'RSA는 소인수분해가 어렵다는 점을 써요.',
          '론 리베스트가 만들었어요.',
          '자세한 건 RSA 위키를 보세요',
          '표준 문서 참고',
          '',
          '끝',
          '남은 표시',
        ].join('\n'),
      ],
    ]);
  });

  it('ChatGPT 첨부는 이미지와 문서를 나눠 표시하고 이미지를 두 번 붙이지 않는다', () => {
    const image = { id: 'f1', name: 'board.png', mime_type: 'image/png' };
    const pdf = {
      id: 'f2',
      name: '강의노트.pdf',
      mime_type: 'application/pdf',
    };
    // 길게 붙여 넣은 글도 첨부가 되고, 내보내기에는 내용이 없다
    const paste = {
      id: 'f3',
      name: 'Pasted text.txt',
      mime_type: 'text/plain',
      is_big_paste: true,
    };
    const pointer = { content_type: 'image_asset_pointer' };

    expect(
      gptTexts([
        gptMessage('user', [pointer, '칠판 사진이에요'], {
          contentType: 'multimodal_text',
          attachments: [image],
        }),
        gptMessage('user', ['노트 요약해 주세요'], { attachments: [pdf] }),
        gptMessage('user', ['이거 봐 주세요'], { attachments: [paste] }),
        gptMessage('user', [pointer, '둘 다'], {
          contentType: 'multimodal_text',
          attachments: [image, pdf],
        }),
        // 이미지 조각 없이 이미지 첨부만 있는 경우
        gptMessage('user', ['사진만 첨부'], { attachments: [image] }),
        // 종류가 비어 있는 첨부는 문서로 본다
        gptMessage('user', ['모르는 파일'], {
          attachments: [{ id: 'f4', name: 'x', mime_type: '' }],
        }),
      ]).map(([, text]) => text),
    ).toEqual([
      '[이미지]\n칠판 사진이에요',
      '노트 요약해 주세요\n[첨부 파일]',
      '이거 봐 주세요\n[첨부 파일]',
      '[이미지]\n둘 다\n[첨부 파일]',
      '사진만 첨부\n[이미지]',
      '모르는 파일\n[첨부 파일]',
    ]);
  });

  it('ChatGPT 생각 과정·맞춤 지침·화면에 안 보이는 메시지는 가져오지 않는다', () => {
    expect(
      gptTexts([
        // 맞춤 지침(자기소개가 들어 있다)
        {
          author: { role: 'user' },
          content: {
            content_type: 'user_editable_context',
            user_profile: '저는 OO대 학생이에요',
          },
        },
        gptMessage('user', ['교착 상태가 뭐예요?']),
        {
          author: { role: 'assistant' },
          content: { content_type: 'thoughts', thoughts: [] },
        },
        {
          author: { role: 'assistant' },
          content: { content_type: 'reasoning_recap', content: '생각함' },
        },
        {
          author: { role: 'assistant' },
          content: { content_type: 'text', parts: ['숨은 메시지'] },
          metadata: { is_visually_hidden_from_conversation: true },
        },
        gptMessage('assistant', ['서로 자원을 기다리는 상태예요.']),
      ]),
    ).toEqual([
      ['user', '교착 상태가 뭐예요?'],
      ['assistant', '서로 자원을 기다리는 상태예요.'],
    ]);
  });

  it('Claude 파일에서 글만 뽑고 이미지·첨부는 표시만 남긴다', () => {
    const result = parseConversationExport('conversations.json', claudeFile());
    if (!result.ok) throw new Error(result.message);
    expect(result.source).toBe('claude');
    expect(result.skippedCount).toBe(1);

    const [conversation] = result.conversations;
    expect(conversation.title).toBe('운영체제 교착 상태');
    expect(conversation.createdAt).toBe('2026-10-20T10:00:00.000Z');
    expect(conversation.messages.map((m) => [m.role, m.text])).toEqual([
      ['user', '교착 상태가 뭐예요?\n[이미지]\n[첨부 파일]'],
      ['assistant', '서로 자원을 기다리며 멈추는 상태예요.'],
    ]);
    // 첨부 문서의 내용은 가져오지 않는다
    expect(JSON.stringify(result.conversations)).not.toContain(
      '개인 노트 내용',
    );
  });

  it('Claude 실제 파일처럼 글이 하나도 없는 대화는 건너뛴다', () => {
    const result = parseConversationExport(
      'conversations.json',
      JSON.stringify([
        claudeConversation('ok', [
          { sender: 'human', text: '페이징이 뭐예요?', content: [] },
        ]),
        // 메시지 칸은 있지만 글·블록·파일이 모두 비어 있다
        claudeConversation('blank', [
          { sender: 'human', text: '', content: [] },
          { sender: 'assistant', text: '', content: [] },
        ]),
      ]),
    );
    if (!result.ok) throw new Error(result.message);
    expect(result.conversations.map((c) => c.id)).toEqual(['ok']);
    expect(result.skippedCount).toBe(1);
  });

  it('제목이 비어 있으면 첫 질문 앞부분으로 채운다', () => {
    const long =
      '운영체제   스케줄링에서\n라운드 로빈과 우선순위 방식의 차이를 예시와 함께 자세히 알려 주세요';
    const result = parseConversationExport(
      'conversations.json',
      JSON.stringify([
        claudeConversation('short', [
          { sender: 'assistant', text: '안녕하세요', content: [] },
          { sender: 'human', text: '교착 상태가 뭐예요?', content: [] },
        ]),
        claudeConversation('long', [
          { sender: 'human', text: long, content: [] },
        ]),
        claudeConversation('no-question', [
          { sender: 'assistant', text: '답변만 있어요', content: [] },
        ]),
      ]),
    );
    if (!result.ok) throw new Error(result.message);
    const titles = result.conversations.map((c) => c.title);
    expect(titles[0]).toBe('교착 상태가 뭐예요?');
    // 줄바꿈·연속 공백은 한 칸으로, 40자를 넘으면 자르고 말줄임표
    expect(titles[1]).toBe(
      '운영체제 스케줄링에서 라운드 로빈과 우선순위 방식의 차이를 예시와 함께…',
    );
    expect(titles[2]).toBe('제목 없는 대화');
  });

  it('Claude 첨부는 이미지와 문서를 나눠 표시한다', () => {
    const result = parseConversationExport(
      'conversations.json',
      JSON.stringify([
        claudeConversation('files', [
          {
            sender: 'human',
            text: '문서 파일',
            content: [],
            files: [{ file_name: '강의노트.PDF' }, { file_name: 'data.xlsx' }],
          },
          {
            sender: 'human',
            text: '이미지 파일',
            content: [],
            files: [{ file_name: 'photo.JPEG' }],
          },
          {
            // 실제 파일에는 이름이 null인 파일이 많다. 이미지 블록이 있으면 이미지로 본다
            sender: 'human',
            text: '붙여 넣은 이미지',
            content: [
              { type: 'image' },
              { type: 'text', text: '붙여 넣은 이미지' },
            ],
            files: [{ file_name: null }],
          },
          {
            sender: 'human',
            text: '알 수 없는 파일',
            content: [],
            files: [{ file_name: null }],
          },
          {
            sender: 'human',
            text: '둘 다',
            content: [],
            files: [{ file_name: 'a.png' }, { file_name: 'b.hwp' }],
          },
        ]),
      ]),
    );
    if (!result.ok) throw new Error(result.message);
    expect(result.conversations[0].messages.map((m) => m.text)).toEqual([
      '문서 파일\n[첨부 파일]',
      '이미지 파일\n[이미지]',
      '붙여 넣은 이미지\n[이미지]',
      '알 수 없는 파일\n[첨부 파일]',
      '둘 다\n[이미지]\n[첨부 파일]',
    ]);
  });

  it('JSON이 아니면 이유를 알려 준다', () => {
    const result = parseConversationExport('conversations.json', '{ 깨진 파일');
    expect(result.ok).toBe(false);
    if (!result.ok) expect(result.title).toBe('파일을 읽지 못했어요');
  });

  it('다른 형식의 JSON이면 내보낸 zip을 올리라고 알려 준다', () => {
    for (const text of ['[]', '{}', '[{"a":1}]', '"문자열"']) {
      const result = parseConversationExport('conversations.json', text);
      expect(result.ok).toBe(false);
      if (!result.ok) {
        expect(result.title).toBe('대화 기록 파일이 아니에요');
        expect(result.message).toMatch(/zip/);
      }
    }
  });

  it('글이 있는 대화가 하나도 없으면 실패로 알려 준다', () => {
    const result = parseConversationExport(
      'conversations.json',
      JSON.stringify([{ uuid: 'x', name: 'y', chat_messages: [] }]),
    );
    expect(result.ok).toBe(false);
  });
});

describe('filterByKeywords', () => {
  const parsed = (() => {
    const result = parseConversationExport('conversations.json', claudeFile());
    if (!result.ok) throw new Error(result.message);
    return result.conversations;
  })();

  it('제목이나 메시지에 키워드가 있는 대화만 고르고 찾은 키워드를 알려 준다', () => {
    const matches = filterByKeywords(parsed, ['교착 상태', '페이징']);
    expect(matches).toHaveLength(1);
    expect(matches[0].matchedKeywords).toEqual(['교착 상태']);
  });

  it('대소문자와 앞뒤 공백을 무시한다', () => {
    const rsa = parseConversationExport('conversations.json', chatGptFile());
    if (!rsa.ok) throw new Error(rsa.message);
    expect(filterByKeywords(rsa.conversations, [' rsa '])).toHaveLength(1);
  });

  it('키워드가 없거나 비어 있으면 아무것도 고르지 않는다', () => {
    expect(filterByKeywords(parsed, [])).toEqual([]);
    expect(filterByKeywords(parsed, ['  ', ''])).toEqual([]);
  });

  it('관련 없는 키워드면 고르지 않는다', () => {
    expect(filterByKeywords(parsed, ['스케줄링'])).toEqual([]);
  });
});
