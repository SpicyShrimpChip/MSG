package com.msg.backend.domain.auth.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 회원가입 자동 가입 방지 (Cloudflare Turnstile, app.turnstile). 비밀 키는 환경변수 TURNSTILE_SECRET_KEY로만 받는다.
 * 비밀 키가 비어 있으면 확인하지 않는다(로컬 개발·테스트). 프론트의 사이트 키(VITE_TURNSTILE_SITE_KEY)와 같이 켠다.
 *
 * @param secretKey 비밀 키. 비어 있으면 꺼짐
 * @param verifyUrl 토큰 확인 주소 (테스트에서 바꿔 끼운다)
 * @param timeout   확인 응답을 기다리는 시간
 */
@ConfigurationProperties(prefix = "app.turnstile")
public record TurnstileProperties(String secretKey, String verifyUrl, Duration timeout) {

	public boolean enabled() {
		return secretKey != null && !secretKey.isBlank();
	}
}
