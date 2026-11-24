package com.msg.backend.domain.conversation.entity;

import com.msg.backend.global.common.ApiEnumValues;
import java.util.Optional;

/**
 * 대화를 내보낸 외부 AI 서비스. API에는 소문자(chatgpt, claude)로 주고받는다
 * (frontend/src/types/conversation.ts의 ImportedConversation.source).
 */
public enum ConversationSource {

	CHATGPT,
	CLAUDE;

	public String apiValue() {
		return ApiEnumValues.of(this);
	}

	// 정확히 같은 값만 받는다 (ApiEnumValues.parse와 달리 앞뒤 공백·대문자를 받지 않는다)
	public static Optional<ConversationSource> fromApiValue(String value) {
		for (ConversationSource source : values()) {
			if (source.apiValue().equals(value)) {
				return Optional.of(source);
			}
		}
		return Optional.empty();
	}
}
