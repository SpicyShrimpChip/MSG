import type { ConversationImport, ConversationImportResult } from '@/types';
import { mockImportConversations } from '@/mocks';
// 스토어 배럴(화면 모드 등 window 사용)까지 불러오지 않도록 파일을 직접 가져온다
import { isMockData } from '@/store/useDemoStore';
import { request } from './request';

// 확정: Swagger `대화 가져오기`. 개인정보가 남은 대화가 있으면 422이고, ApiError.data에 ConversationImportRejection이 있다
export function importConversations(
  input: ConversationImport,
): Promise<ConversationImportResult> {
  if (isMockData()) return mockImportConversations(input);
  return request<ConversationImportResult>({
    method: 'POST',
    url: '/conversations',
    data: input,
    // 대화 본문이 길어 기본 5초보다 오래 걸릴 수 있다
    timeout: 30_000,
  });
}
