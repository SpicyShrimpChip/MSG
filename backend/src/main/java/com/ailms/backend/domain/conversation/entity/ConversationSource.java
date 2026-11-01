package com.ailms.backend.domain.conversation.entity;

import java.util.Locale;
import java.util.Optional;

/**
 * 대화를 내보낸 외부 AI 서비스. API에는 소문자(chatgpt, claude)로 주고받는다
 * (frontend/src/types/conversation.ts의 ImportedConversation.source).
 */
public enum ConversationSource {

	CHATGPT,
	CLAUDE;

	public String apiValue() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static Optional<ConversationSource> fromApiValue(String value) {
		for (ConversationSource source : values()) {
			if (source.apiValue().equals(value)) {
				return Optional.of(source);
			}
		}
		return Optional.empty();
	}
}
