import { describe, expect, it } from 'vitest';
import {
  MASK_LABELS,
  MASK_LABEL_PATTERN,
  maskConversationWithSpots,
  maskText,
  type MaskKind,
} from './mask';
import type { ParsedConversation } from './types';

describe('maskText', () => {
  it('이메일을 가린다', () => {
    const result = maskText('메일은 test@example.com 으로 보내 주세요');
    expect(result.text).toBe('메일은 [이메일] 으로 보내 주세요');
    expect(result.matches).toHaveLength(1);
    expect(result.matches[0]).toMatchObject({
      kind: 'email',
      original: 'test@example.com',
    });
  });

  it.each([
    '010-1234-5678',
    '01012345678',
    '010 1234 5678',
    '+82 10-1234-5678',
    '02-123-4567',
    '031-123-4567',
    '070-1234-5678',
  ])('전화번호 %s 를 가린다', (phone) => {
    expect(maskText(`전화 ${phone} 입니다`).text).toBe(
      '전화 [전화번호] 입니다',
    );
  });

  it('전화번호가 아닌 숫자는 가리지 않는다', () => {
    const text = '2026년 10월 25일 12345 포트 8080';
    expect(maskText(text).text).toBe(text);
  });

  it('날짜가 맞는 주민등록번호만 가린다', () => {
    expect(maskText('900101-1234567').text).toBe('[주민등록번호]');
    expect(maskText('9001011234567').text).toBe('[주민등록번호]');
    // 13월은 날짜가 아니다
    expect(
      maskText('901301-1234567').matches.some((m) => m.kind === 'rrn'),
    ).toBe(false);
  });

  it('Luhn 검사를 통과한 카드번호만 가린다', () => {
    expect(maskText('카드 4111 1111 1111 1111 입니다').text).toBe(
      '카드 [카드번호] 입니다',
    );
    expect(maskText('4111-1111-1111-1111').text).toBe('[카드번호]');
    const notCard = '1234 5678 9012 3456';
    expect(maskText(notCard).text).toBe(notCard);
  });

  it('API 키와 토큰을 가린다', () => {
    const samples = [
      'sk-proj-abcdefghijklmnop1234',
      'sk-ant-api03-abcdefghijklmnopqrstuv',
      'ghp_abcdefghijklmnopqrstuvwxyz0123456789',
      'AKIAIOSFODNN7EXAMPLE',
      'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.abcdefghijk',
      'Bearer abcdefghijklmnopqrstuvwxyz123456',
    ];
    for (const sample of samples) {
      expect(maskText(`키: ${sample}`).text).toBe('키: [키·토큰]');
    }
  });

  it('IPv4 주소를 가리고 범위를 벗어난 숫자는 그대로 둔다', () => {
    expect(maskText('서버 192.168.0.1 접속').text).toBe('서버 [IP] 접속');
    expect(maskText('999.1.1.1').text).toBe('999.1.1.1');
  });

  it('내 이름과 직접 추가한 단어를 가린다 (대소문자 무시)', () => {
    const result = maskText('저는 변지환이고 SKU 프로젝트 중이에요', {
      names: ['변지환'],
      terms: ['sku'],
    });
    expect(result.text).toBe('저는 [이름]이고 [가린 단어] 프로젝트 중이에요');
  });

  it('긴 이름이 짧은 이름보다 먼저 가려진다', () => {
    const result = maskText('김주미 님, 주미 님', {
      names: ['주미', '김주미'],
    });
    expect(result.text).toBe('[이름] 님, [이름] 님');
  });

  it('정규식 특수문자가 들어간 단어도 글자 그대로 찾는다', () => {
    expect(maskText('a.b와 axb', { terms: ['a.b'] }).text).toBe(
      '[가린 단어]와 axb',
    );
  });

  it('겹치면 우선순위가 높은 쪽만 가린다 (이메일 안의 이름)', () => {
    const result = maskText('jihwan@example.com', { names: ['jihwan'] });
    expect(result.text).toBe('[이메일]');
    expect(result.matches).toHaveLength(1);
  });

  it('가린 결과 글 안의 위치를 돌려준다', () => {
    const result = maskText('a test@example.com b 010-1234-5678');
    for (const match of result.matches) {
      expect(result.text.slice(match.start, match.end)).toMatch(/^\[.+\]$/);
    }
    expect(result.matches.map((m) => m.kind)).toEqual(['email', 'phone']);
  });

  it('가릴 것이 없으면 글을 그대로 돌려준다', () => {
    const text = '교착 상태는 4가지 조건이 모두 만족될 때 발생해요.';
    expect(maskText(text)).toEqual({ text, matches: [] });
  });
});

describe('maskConversationWithSpots: 가린 대화', () => {
  const conversation: ParsedConversation = {
    id: 'c1',
    source: 'chatgpt',
    title: '변지환 정보보호 질문',
    messages: [
      { role: 'user', text: '내 메일은 test@example.com 이야' },
      { role: 'assistant', text: '네, 변지환 님' },
    ],
  };

  it('제목과 메시지를 모두 가리고 종류별 개수를 센다', () => {
    const {
      conversation: masked,
      counts,
      totalCount,
    } = maskConversationWithSpots(conversation, {
      names: ['변지환'],
    });
    expect(masked.title).toBe('[이름] 정보보호 질문');
    expect(masked.messages.map((m) => m.text)).toEqual([
      '내 메일은 [이메일] 이야',
      '네, [이름] 님',
    ]);
    expect(counts).toEqual({ name: 2, email: 1 });
    expect(totalCount).toBe(3);
  });

  it('원래 대화는 바꾸지 않는다', () => {
    maskConversationWithSpots(conversation, { names: ['변지환'] });
    expect(conversation.title).toBe('변지환 정보보호 질문');
    expect(conversation.messages[0].text).toContain('test@example.com');
  });
});

describe('maskConversationWithSpots: 가린 곳 목록', () => {
  const conversation: ParsedConversation = {
    id: 'c1',
    source: 'claude',
    title: '홍길동 학점 정리',
    createdAt: undefined,
    messages: [
      { role: 'user', text: '안녕하세요' },
      { role: 'assistant', text: '네' },
      {
        role: 'user',
        text: '제 번호는 010-1234-5678이고\n메일은 me@example.com 이에요',
      },
    ],
  };

  it('제목과 모든 메시지에서 가린 곳을 순서대로 모으고 앞뒤 글을 한 줄로 붙인다', () => {
    const { spots } = maskConversationWithSpots(conversation, {
      names: ['홍길동'],
    });
    expect(
      spots.map(({ messageIndex, kind, original, before, after }) => ({
        messageIndex,
        kind,
        original,
        before,
        after,
      })),
    ).toEqual([
      {
        messageIndex: -1,
        kind: 'name',
        original: '홍길동',
        before: '',
        after: ' 학점 정리',
      },
      {
        messageIndex: 2,
        kind: 'phone',
        original: '010-1234-5678',
        before: '제 번호는 ',
        after: '이고 메일은 [이메일] 이에요',
      },
      {
        messageIndex: 2,
        kind: 'email',
        original: 'me@example.com',
        before: '제 번호는 [전화번호]이고 메일은 ',
        after: ' 이에요',
      },
    ]);
  });

  it('가린 표시가 글 안에서 몇 번째인지(사용자가 쓴 [IP] 글자도 센다) 알려 준다', () => {
    const { spots } = maskConversationWithSpots({
      ...conversation,
      messages: [
        {
          role: 'user',
          text: '표시는 [IP]로 써요. 010-1234-5678 그리고 me@example.com',
        },
      ],
    });
    // 글 안의 표시는 [IP](직접 쓴 것), [전화번호], [이메일] 순서
    expect(
      spots
        .filter((spot) => spot.messageIndex === 0)
        .map((spot) => spot.labelIndex),
    ).toEqual([1, 2]);
  });

  it('가린 곳이 없으면 빈 목록', () => {
    expect(
      maskConversationWithSpots({
        ...conversation,
        title: '정리',
        messages: [],
      }).spots,
    ).toEqual([]);
  });
});

// 가린 표시 글자는 러스트(mask.rs의 MaskKind::label)가 넣고, 화면은 MASK_LABELS로 찾아 칠하고 센다.
// 한쪽만 바꾸면 가린 표시가 칠해지지 않거나 가린 곳 "보기"가 엉뚱한 자리를 짚으므로 종류마다 맞춰 본다
describe('가린 표시 글자: 러스트와 MASK_LABELS', () => {
  // 종류별로 하나씩만 걸리는 글 (Record라 종류가 늘면 여기서 타입 오류가 난다)
  const SAMPLES: Record<MaskKind, string> = {
    secret: 'sk-proj-abcdefghijklmnop1234',
    email: 'test@example.com',
    rrn: '900101-1234567',
    card: '4111 1111 1111 1111',
    phone: '010-1234-5678',
    ip: '192.168.0.1',
    name: '홍길동',
    custom: '비밀단어',
  };

  it.each(Object.keys(SAMPLES) as MaskKind[])('%s', (kind) => {
    const { text, matches } = maskText(`앞 ${SAMPLES[kind]} 뒤`, {
      names: ['홍길동'],
      terms: ['비밀단어'],
    });
    expect(matches.map((match) => match.kind)).toEqual([kind]);
    expect(text.slice(matches[0].start, matches[0].end)).toBe(
      MASK_LABELS[kind],
    );
    expect(text).toBe(`앞 ${MASK_LABELS[kind]} 뒤`);
  });

  it('가린 표시 정규식이 모든 표시를 찾는다', () => {
    const labels = Object.values(MASK_LABELS);
    expect(labels.join(' ').match(MASK_LABEL_PATTERN)).toEqual(labels);
  });
});
