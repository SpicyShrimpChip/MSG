package com.msg.backend.domain.auth.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 로그인 세션 설정 (app.auth). 값은 application*.yaml에서 환경별로 다르게 둔다.
 */
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(Duration refreshTokenTtl, RefreshCookie refreshCookie, LoginAttempt loginAttempt) {

	public record RefreshCookie(String name, String path, boolean secure, String sameSite) {
	}

	public record LoginAttempt(int maxFailures, Duration window, Duration lockDuration, int maxFailuresPerIp) {
	}
}
