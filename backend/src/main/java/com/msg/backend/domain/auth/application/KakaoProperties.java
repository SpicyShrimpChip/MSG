package com.msg.backend.domain.auth.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 카카오 로그인 (app.oauth.kakao). 키는 환경변수(KAKAO_REST_API_KEY·KAKAO_CLIENT_SECRET·KAKAO_ADMIN_KEY)로만 받는다.
 * REST API 키·Client Secret·Redirect URI 중 하나라도 비면 카카오 로그인을 끈다(로컬·테스트·CI).
 *
 * @param clientId     REST API 키
 * @param clientSecret 토큰 요청에 함께 보내는 Client Secret
 * @param adminKey     탈퇴 때 연결 끊기(unlink)에 쓰는 어드민 키. 비면 연결 끊기를 건너뛴다
 * @param redirectUri  카카오 콘솔에 등록한 Redirect URI (사용자 주소의 /api/auth/kakao/callback)
 * @param authBaseUrl  인가·토큰 서버 (테스트에서 가짜 서버로 바꾼다)
 * @param apiBaseUrl   사용자 정보·연결 끊기 서버 (테스트에서 가짜 서버로 바꾼다)
 * @param timeout      카카오 응답을 기다리는 시간
 */
@ConfigurationProperties(prefix = "app.oauth.kakao")
public record KakaoProperties(String clientId, String clientSecret, String adminKey, String redirectUri,
		String authBaseUrl, String apiBaseUrl, Duration timeout) {

	public KakaoProperties {
		if (timeout == null || timeout.isNegative() || timeout.isZero()) {
			timeout = Duration.ofSeconds(5);
		}
	}

	public boolean enabled() {
		return hasText(clientId) && hasText(clientSecret) && hasText(redirectUri);
	}

	public boolean hasAdminKey() {
		return hasText(adminKey);
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}
}
