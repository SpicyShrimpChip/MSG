// AI 대화 가져오기 요청·응답 (POST /api/conversations). docs/frontend-requests.md "대화 가져오기"와 같은 모양
export interface ImportedMessage {
  role: 'user' | 'assistant';
  // 20,000자까지. 더 긴 메시지는 나눠서 보낸다
  text: string;
  // 나눈 메시지에서 이어지는 글이 다음 메시지에 있으면 true (마지막 조각에는 없다)
  continued?: boolean;
}

export interface ImportedConversation {
  // 내보내기 파일 안의 대화 id. 같은 대화를 다시 보내면 서버가 중복 저장하지 않는다
  clientId: string;
  source: 'chatgpt' | 'claude';
  // 500자까지 (넘으면 잘라 보낸다)
  title: string;
  createdAt?: string;
  messages: ImportedMessage[];
  // 메시지가 너무 많아 나눠 보낸 조각일 때만 있다. 서버는 parentClientId가 같은 조각을 index 순서로 한 대화에 이어 붙인다
  part?: ConversationPart;
}

export interface ConversationPart {
  parentClientId: string;
  index: number; // 1부터
  count: number;
}

// 요청 한 번. 보내기를 한 번 누를 때 만든 importId를 모든 요청에 붙이고, 요청마다 번호(1부터)와 전체 수를 붙인다.
// 서버는 전체 수만큼 요청을 받아야 가져오기를 끝난 것으로 보고, 그 전에는 대화를 분석에 쓰지 않는다
export interface ConversationImport {
  importId: string;
  requestIndex: number;
  requestCount: number;
  courseId: number;
  conversations: ImportedConversation[];
}

export interface RejectedConversation {
  clientId: string;
  // 사용자에게 그대로 보여 줄 문구
  reason: string;
}

// 성공 응답 (201). 같은 요청을 다시 보내도 저장하지 않고 같은 결과를 돌려준다
export interface ConversationImportResult {
  importedCount: number;
  // 이 가져오기의 요청을 모두 받았는지
  importCompleted: boolean;
}

// 422 응답의 data. 이 대화들 때문에 요청 전체를 저장하지 않았다
export interface ConversationImportRejection {
  rejected: RejectedConversation[];
}
