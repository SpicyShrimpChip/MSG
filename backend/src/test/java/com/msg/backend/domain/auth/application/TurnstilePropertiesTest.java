package com.msg.backend.domain.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

// 운영(required=true)에서 비밀 키를 빠뜨리면 서버가 뜨지 않는다
class TurnstilePropertiesTest {

	private static final String URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify";

	@Test
	void 꼭_있어야_하는데_비밀_키가_비면_설정을_만들지_않는다() {
		assertThatThrownBy(() -> new TurnstileProperties("", URL, Duration.ofSeconds(5), true))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("TURNSTILE_SECRET_KEY");
		assertThatThrownBy(() -> new TurnstileProperties(null, URL, Duration.ofSeconds(5), true))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void 꼭_있어야_하지_않으면_비밀_키가_비어도_꺼진_채로_만든다() {
		assertThat(new TurnstileProperties("", URL, Duration.ofSeconds(5), false).enabled()).isFalse();
		assertThat(new TurnstileProperties("key", URL, Duration.ofSeconds(5), true).enabled()).isTrue();
	}
}
