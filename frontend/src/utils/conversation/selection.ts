import { conversationKey } from './merge';
import type { ConversationToSend } from './send';
import type { ParsedConversation } from './types';

// 대화마다 들어갈 과목과 보낼지 여부 (키: conversationKey)
export type ConversationChoices = Record<
  string,
  { courseId: number; selected: boolean }
>;

export interface ChosenConversation {
  courseId: number;
  // 가리기 전 원래 대화. 서버로 보내지 않고 가리기에만 넘긴다
  conversation: ParsedConversation;
}

// 고른 대화만 읽은 순서대로, 고른 과목과 함께 돌려준다 (고르지 않았거나 선택 정보가 없는 대화는 빠진다)
export function chosenConversations(
  conversations: ParsedConversation[],
  choices: ConversationChoices,
): ChosenConversation[] {
  return conversations.flatMap((conversation) => {
    const choice = choices[conversationKey(conversation)];
    return choice?.selected
      ? [{ courseId: choice.courseId, conversation }]
      : [];
  });
}

// 보낼 대화: 고른 대화의 과목에 가린 결과의 대화만 붙인다. masked[i]는 chosen[i]를 가린 결과다
// (chosen의 대화를 같은 순서로 가렸다). 원래 대화와 가린 곳 목록(spots, 원문이 들어 있다)은 넣지 않는다
export function pairWithMasked(
  chosen: ChosenConversation[],
  masked: { conversation: ParsedConversation }[],
): ConversationToSend[] {
  return chosen.map(({ courseId }, index) => ({
    courseId,
    conversation: masked[index].conversation,
  }));
}
