import { ApiError } from '@/api/request';
import type { ConversationImport, ConversationImportResult } from '@/types';
import { maskText } from '@/utils/conversation/mask';
import { mockDelay } from './delay';

// 서버(PersonalInfoDetector)가 하는 개인정보 재검사를 흉내 낸다: 가리지 않은 개인정보가 남은 대화가 하나라도 있으면
// 요청 전체를 저장하지 않고 422와 그 대화 목록을 돌려준다
export async function mockImportConversations(
  input: ConversationImport,
): Promise<ConversationImportResult> {
  const rejected = input.conversations
    .filter(({ title, messages }) =>
      [title, ...messages.map((message) => message.text)].some(
        (text) => maskText(text).matches.length > 0,
      ),
    )
    .map(({ clientId }) => ({
      clientId,
      reason:
        '개인정보로 보이는 글이 남아 있어 저장하지 않았어요. 가린 내용을 다시 확인해 주세요.',
    }));
  await mockDelay(null, 600);
  if (rejected.length > 0) {
    throw new ApiError(
      '저장할 수 없는 대화가 있어 이번에 보낸 대화를 저장하지 않았어요.',
      422,
      { rejected },
    );
  }
  return {
    importedCount: input.conversations.length,
    importCompleted: input.requestIndex === input.requestCount,
  };
}
