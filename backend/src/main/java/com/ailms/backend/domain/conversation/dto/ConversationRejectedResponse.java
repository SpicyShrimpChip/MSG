package com.ailms.backend.domain.conversation.dto;

import java.util.List;

// 422 응답의 data. 이 목록의 대화 때문에 요청 전체를 저장하지 않았다
public record ConversationRejectedResponse(List<RejectedConversation> rejected) {
}
