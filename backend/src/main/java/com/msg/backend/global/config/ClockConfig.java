package com.msg.backend.global.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// 현재 시각은 이 Clock으로만 얻는다 (토큰 만료 같은 시간 의존 로직을 테스트에서 바꿔 끼울 수 있게)
@Configuration
public class ClockConfig {

	@Bean
	public Clock clock() {
		return Clock.systemDefaultZone();
	}
}
