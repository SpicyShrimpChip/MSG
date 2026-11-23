package com.msg.backend.domain.auth.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 로그인 세션 설정 (app.auth). 값은 application*.yaml에서 환경별로 다르게 둔다.
 *
 * @param refreshReuseGrace 회전으로 방금 폐기된 Refresh Token을 재사용으로 보지 않는 시간 (탭 여러 개가 동시에 갱신할 때, #211).
 *                          비어 있으면 0(유예 없음)이다
 */
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(Duration refreshTokenTtl, Duration refreshReuseGrace, RefreshCookie refreshCookie,
		LoginAttempt loginAttempt) {

	public AuthProperties {
		if (refreshReuseGrace == null || refreshReuseGrace.isNegative()) {
			refreshReuseGrace = Duration.ZERO;
		}
	}

	public record RefreshCookie(String name, String path, boolean secure, String sameSite) {
	}

	public record LoginAttempt(int maxFailures, Duration window, Duration lockDuration, int maxFailuresPerIp) {
	}
}
