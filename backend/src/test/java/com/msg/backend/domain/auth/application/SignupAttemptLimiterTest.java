package com.msg.backend.domain.auth.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.msg.backend.global.error.BusinessException;
import com.msg.backend.support.MutableClock;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class SignupAttemptLimiterTest {

	private static final String IP = "10.0.0.1";

	private final MutableClock clock = new MutableClock();

	@Test
	void 한_IP가_기간_안에_상한만큼_시도하면_막고_가장_오래된_시도가_빠지면_풀린다() {
		SignupAttemptLimiter limiter = limiter(3, Duration.ofHours(1));
		limiter.acquire(IP);
		clock.advance(Duration.ofMinutes(20));
		limiter.acquire(IP);
		limiter.acquire(IP);

		assertThatThrownBy(() -> limiter.acquire(IP))
				.isInstanceOf(BusinessException.class)
				.hasMessage("같은 곳에서 가입 시도가 너무 많아요. 40분 뒤 다시 시도해 주세요.");

		// 막힌 시도는 세지 않으므로, 첫 시도가 기간 밖으로 나가면 한 번 더 할 수 있다
		clock.advance(Duration.ofMinutes(40));
		assertThatCode(() -> limiter.acquire(IP)).doesNotThrowAnyException();
		assertThatThrownBy(() -> limiter.acquire(IP)).isInstanceOf(BusinessException.class);
	}

	@Test
	void 다른_IP는_따로_센다() {
		SignupAttemptLimiter limiter = limiter(1, Duration.ofHours(1));
		limiter.acquire(IP);

		assertThatCode(() -> limiter.acquire("10.0.0.2")).doesNotThrowAnyException();
		assertThatThrownBy(() -> limiter.acquire(IP)).isInstanceOf(BusinessException.class);
	}

	@Test
	void 상한이_0이면_막지_않는다() {
		SignupAttemptLimiter limiter = limiter(0, Duration.ofHours(1));

		for (int i = 0; i < 100; i++) {
			limiter.acquire(IP);
		}
	}

	private SignupAttemptLimiter limiter(int maxPerIp, Duration window) {
		return new SignupAttemptLimiter(new AuthProperties(Duration.ofDays(14), Duration.ZERO, null, null,
				new AuthProperties.SignupAttempt(maxPerIp, window)), clock);
	}
}
