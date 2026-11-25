import type { ConversationSource } from '@/utils';

// 대화를 내보낸 서비스 이름 (대화 보기 말풍선·대화 목록에서 같이 쓴다)
export const SOURCE_NAME: Record<ConversationSource, string> = {
  chatgpt: 'ChatGPT',
  claude: 'Claude',
};
