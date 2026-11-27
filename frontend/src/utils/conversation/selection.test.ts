import { describe, expect, it } from 'vitest';
import { maskConversationWithSpots } from './mask';
import { conversationKey } from './merge';
import {
  chosenConversations,
  pairWithMasked,
  type ConversationChoices,
} from './selection';
import { buildImportRequests } from './send';
import type { ParsedConversation } from './types';

const IMPORT_ID = '0b7c2f0e-8d4a-4c55-9a51-2f7e3b9d1c00';

// 원문에만 있는 글자. 요청 본문에 이 글자가 하나라도 있으면 가리기 전 대화가 섞인 것이다
const RAW_EMAIL = 'kim.minsu@example.com';
const RAW_PHONE = '010-2468-1357';
const RAW_NAME = '김민수';

function conversation(id: string, text: string): ParsedConversation {
  return {
    id,
    source: 'chatgpt',
    title: `${RAW_NAME}의 해시 함수 질문 ${id}`,
    createdAt: '2026-10-20T09:00:00.000Z',
    messages: [
      { role: 'user', text },
      { role: 'assistant', text: '해시 함수는 같은 입력에 같은 값을 낸다' },
    ],
  };
}

const a = conversation('a', `해시 함수가 뭐야? 답은 ${RAW_EMAIL}로 줘`);
const b = conversation('b', `충돌 저항성은? 전화는 ${RAW_PHONE}`);
const c = conversation('c', '고르지 않은 대화: 비밀 일기');
const d = conversation('d', `전자서명은? ${RAW_EMAIL}`);

const choices: ConversationChoices = {
  [conversationKey(a)]: { courseId: 2, selected: true },
  [conversationKey(b)]: { courseId: 1, selected: true },
  [conversationKey(c)]: { courseId: 1, selected: false },
  // d는 선택 정보가 없다 (키워드에 걸리지 않은 대화)
};

// 화면과 같은 흐름: 고른 대화만 → 가리기(Web Worker와 같은 함수) → 짝짓기 → 요청 나누기
function requestBodies(conversations: ParsedConversation[]) {
  const chosen = chosenConversations(conversations, choices);
  const masked = chosen.map(({ conversation }) =>
    maskConversationWithSpots(conversation, { names: [RAW_NAME] }),
  );
  const toSend = pairWithMasked(chosen, masked);
  return { toSend, requests: buildImportRequests(toSend, IMPORT_ID) };
}

describe('고른 대화만, 가린 결과로 보내기', () => {
  it('고른 대화만 읽은 순서대로 고른 과목과 함께 남긴다', () => {
    const chosen = chosenConversations([a, b, c, d], choices);
    expect(chosen.map(({ conversation }) => conversation.id)).toEqual([
      'a',
      'b',
    ]);
    expect(chosen.map(({ courseId }) => courseId)).toEqual([2, 1]);
  });

  it('가린 결과를 같은 순서의 고른 대화와 짝짓고 과목을 지킨다', () => {
    const { toSend } = requestBodies([b, c, a, d]);
    expect(
      toSend.map(({ courseId, conversation }) => [courseId, conversation.id]),
    ).toEqual([
      [1, 'b'],
      [2, 'a'],
    ]);
    // 가린 곳 목록(spots, 원문 포함)이나 원래 대화는 붙이지 않는다
    for (const item of toSend) {
      expect(Object.keys(item).sort()).toEqual(['conversation', 'courseId']);
    }
  });

  it('요청 본문에 가리기 전 원문 글자와 고르지 않은 대화가 없다', () => {
    const { requests } = requestBodies([a, b, c, d]);
    const body = JSON.stringify(requests);

    expect(body).not.toContain(RAW_EMAIL);
    expect(body).not.toContain(RAW_PHONE);
    expect(body).not.toContain(RAW_NAME);
    expect(body).not.toContain('비밀 일기');
    const sentIds = requests.flatMap((request) =>
      request.conversations.map(({ clientId }) => clientId),
    );
    expect(sentIds.sort()).toEqual(['a', 'b']);
    // 가렸다는 표시는 남아 있다 (아무것도 안 보내서 통과하는 것이 아니다)
    expect(body).toContain('[이메일]');
    expect(body).toContain('해시 함수');
  });
});
