package com.ailms.backend.domain.conversation.entity;

import java.util.Locale;

// 메시지를 쓴 쪽. API에는 소문자(user, assistant)로 주고받는다
public enum MessageRole {

	USER,
	ASSISTANT;

	public static MessageRole fromApiValue(String value) {
		return valueOf(value.toUpperCase(Locale.ROOT));
	}
}
