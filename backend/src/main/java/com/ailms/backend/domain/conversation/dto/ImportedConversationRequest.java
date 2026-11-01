package com.ailms.backend.domain.conversation.dto;

import com.ailms.backend.domain.conversation.entity.Conversation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;

// 개인정보를 가린 대화 하나 (frontend/src/types/conversation.ts의 ImportedConversation)
public record ImportedConversationRequest(
		@Schema(description = "내보내기 파일 안의 대화 id. 나눠 보낸 조각은 '<원래 id>#<번호>'")
		@NotBlank(message = "대화 정보가 올바르지 않아요.")
		@Size(max = Conversation.CLIENT_ID_MAX_LENGTH, message = "대화 정보가 올바르지 않아요.")
		String clientId,

		@Schema(allowableValues = {"chatgpt", "claude"})
		@NotNull(message = "대화를 내보낸 서비스를 알 수 없어요.")
		@Pattern(regexp = "^(chatgpt|claude)$", message = "ChatGPT·Claude 대화만 가져올 수 있어요.")
		String source,

		@NotNull(message = "대화 제목이 없어요.")
		@Size(max = Conversation.TITLE_MAX_LENGTH, message = "대화 제목은 500자까지 보낼 수 있어요.")
		String title,

		@Schema(description = "외부 서비스에서 대화를 시작한 시각 (ISO). 없으면 비운다")
		OffsetDateTime createdAt,

		@NotNull(message = "메시지가 없는 대화는 보낼 수 없어요.")
		@Size(min = 1, message = "메시지가 없는 대화는 보낼 수 없어요.")
		List<@NotNull(message = "메시지가 올바르지 않아요.") @Valid ImportedMessageRequest> messages,

		@Schema(description = "긴 대화를 나눠 보낸 조각일 때만 있다")
		@Valid ConversationPartRequest part) {
}
