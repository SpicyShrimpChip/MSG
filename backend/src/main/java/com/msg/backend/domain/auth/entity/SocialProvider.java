package com.msg.backend.domain.auth.entity;

import java.util.Locale;

// 소셜 로그인 제공사. DB에는 이름(KAKAO)으로, API·주소에는 소문자(kakao)로 쓴다
public enum SocialProvider {
	KAKAO;

	public String apiValue() {
		return name().toLowerCase(Locale.ROOT);
	}
}
