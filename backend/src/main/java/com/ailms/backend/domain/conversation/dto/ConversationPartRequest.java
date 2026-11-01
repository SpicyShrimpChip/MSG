package com.ailms.backend.domain.conversation.dto;

import com.ailms.backend.domain.conversation.entity.Conversation;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 긴 대화를 나눠 보낸 조각의 정보 (frontend/src/types/conversation.ts의 ConversationPart)
public record ConversationPartRequest(
		@NotBlank(message = "대화 정보가 올바르지 않아요.")
		@Size(max = Conversation.CLIENT_ID_MAX_LENGTH, message = "대화 정보가 올바르지 않아요.")
		String parentClientId,

		@NotNull(message = "대화 정보가 올바르지 않아요.")
		@Min(value = 1, message = "대화 정보가 올바르지 않아요.")
		Integer index,

		@NotNull(message = "대화 정보가 올바르지 않아요.")
		@Min(value = 1, message = "대화 정보가 올바르지 않아요.")
		@Max(value = MAX_PART_COUNT, message = "대화가 너무 길어요.")
		Integer count) {

	// 조각 하나가 1MB 가까이 되므로 1,000조각이면 1GB다. 정상 대화에서는 닿지 않는다
	public static final int MAX_PART_COUNT = 1_000;
}
