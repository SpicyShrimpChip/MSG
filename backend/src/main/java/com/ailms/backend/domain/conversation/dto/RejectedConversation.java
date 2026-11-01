package com.ailms.backend.domain.conversation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

// 저장하지 않은 대화 (조각이면 조각의 clientId)
public record RejectedConversation(
		String clientId,
		@Schema(description = "사용자에게 그대로 보여 줄 문구") String reason) {
}
