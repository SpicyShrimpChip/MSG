package com.msg.backend.domain.auth.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.msg.backend.global.error.BusinessException;
import com.msg.backend.support.MutableClock;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LoginAttemptLimiterTest {

	private static final String EMAIL = "user@example.com";
	private static final String IP = "10.0.0.1";

	private MutableClock clock;
	private LoginAttemptLimiter limiter;

	@BeforeEach
	void setUp() {
		clock = new MutableClock();
		var config = new AuthProperties.LoginAttempt(3, Duration.ofMinutes(15), Duration.ofMinutes(10), 5);
		limiter = new LoginAttemptLimiter(new AuthProperties(Duration.ofDays(14), Duration.ZERO, null, config), clock);
	}

	@Test
	void 기준_횟수만큼_틀리면_막고_시간이_지나면_풀린다() {
		failTimes(EMAIL, IP, 3);

		assertThatThrownBy(() -> limiter.checkAllowed(EMAIL, IP))
				.isInstanceOf(BusinessException.class)
				.hasMessage("로그인 시도가 너무 많아요. 10분 뒤 다시 시도해 주세요.");

		clock.advance(Duration.ofMinutes(10));
		assertThatCode(() -> limiter.checkAllowed(EMAIL, IP)).doesNotThrowAnyException();
	}

	@Test
	void 다른_IP에서는_같은_이메일로_계속_로그인할_수_있다() {
		failTimes(EMAIL, IP, 3);

		assertThatCode(() -> limiter.checkAllowed(EMAIL, "10.0.0.2")).doesNotThrowAnyException();
	}

	@Test
	void 성공하면_실패_기록을_지운다() {
		failTimes(EMAIL, IP, 2);
		limiter.recordSuccess(EMAIL, IP);
		failTimes(EMAIL, IP, 2);

		assertThatCode(() -> limiter.checkAllowed(EMAIL, IP)).doesNotThrowAnyException();
	}

	@Test
	void 기간이_지난_실패는_세지_않는다() {
		failTimes(EMAIL, IP, 2);
		clock.advance(Duration.ofMinutes(16));
		failTimes(EMAIL, IP, 2);

		assertThatCode(() -> limiter.checkAllowed(EMAIL, IP)).doesNotThrowAnyException();
	}

	@Test
	void 한_IP에서_여러_이메일로_시도하면_IP_기준으로_막는다() {
		for (int i = 0; i < 5; i++) {
			limiter.recordFailure("user" + i + "@example.com", IP);
		}

		assertThatThrownBy(() -> limiter.checkAllowed("new@example.com", IP))
				.isInstanceOf(BusinessException.class);
	}

	private void failTimes(String email, String ip, int times) {
		for (int i = 0; i < times; i++) {
			limiter.recordFailure(email, ip);
		}
	}
}
