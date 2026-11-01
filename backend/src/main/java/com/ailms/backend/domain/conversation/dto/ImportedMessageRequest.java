package com.ailms.backend.domain.conversation.dto;

import com.ailms.backend.domain.conversation.entity.ConversationMessage;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ImportedMessageRequest(
		@Schema(allowableValues = {"user", "assistant"})
		@NotNull(message = "메시지가 올바르지 않아요.")
		@Pattern(regexp = "^(user|assistant)$", message = "메시지가 올바르지 않아요.")
		String role,

		@NotNull(message = "메시지가 올바르지 않아요.")
		@Size(max = ConversationMessage.CONTENT_MAX_LENGTH, message = "메시지 하나는 20,000자까지 보낼 수 있어요.")
		String text,

		@Schema(description = "20,000자가 넘어 나눈 메시지에서 이어지는 글이 다음 메시지에 있으면 true")
		Boolean continued) {

	public boolean isContinued() {
		return Boolean.TRUE.equals(continued);
	}
}
