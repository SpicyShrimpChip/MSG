package com.msg.backend.domain.auth.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 로그인 세션 설정 (app.auth). 값은 application*.yaml에서 환경별로 다르게 둔다.
 *
 * @param refreshReuseGrace 회전으로 방금 폐기된 Refresh Token을 재사용으로 보지 않는 시간 (탭 여러 개가 동시에 갱신할 때, #211).
 *                          비어 있으면 0(유예 없음)이다
 * @param signupAttempt     한 IP의 가입 시도 상한 (SignupAttemptLimiter). 비어 있으면 제한하지 않는다
 */
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(Duration refreshTokenTtl, Duration refreshReuseGrace, RefreshCookie refreshCookie,
		LoginAttempt loginAttempt, SignupAttempt signupAttempt) {

	public AuthProperties {
		if (refreshReuseGrace == null || refreshReuseGrace.isNegative()) {
			refreshReuseGrace = Duration.ZERO;
		}
		if (signupAttempt == null) {
			signupAttempt = new SignupAttempt(0, Duration.ofHours(1));
		}
	}

	public record RefreshCookie(String name, String path, boolean secure, String sameSite) {
	}

	public record LoginAttempt(int maxFailures, Duration window, Duration lockDuration, int maxFailuresPerIp) {
	}

	/**
	 * @param maxPerIp 한 IP가 window 동안 시도할 수 있는 가입 수. 0 이하면 제한하지 않는다
	 * @param window   세는 기간
	 */
	public record SignupAttempt(int maxPerIp, Duration window) {
	}
}
