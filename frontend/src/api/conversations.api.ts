import type { ConversationImport, ConversationImportResult } from '@/types';
import { mockImportConversations } from '@/mocks';
import { USE_MOCK } from './axios';
import { request } from './request';

// 확정: Swagger `대화 가져오기`. 개인정보가 남은 대화가 있으면 422이고, ApiError.data에 ConversationImportRejection이 있다
export function importConversations(
  input: ConversationImport,
): Promise<ConversationImportResult> {
  if (USE_MOCK) return mockImportConversations(input);
  return request<ConversationImportResult>({
    method: 'POST',
    url: '/conversations',
    data: input,
    // 대화 본문이 길어 기본 5초보다 오래 걸릴 수 있다
    timeout: 30_000,
  });
}
