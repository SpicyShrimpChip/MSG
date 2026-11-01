package com.ailms.backend.support;

import java.util.Map;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;

/**
 * 통합 테스트는 실제 AI 제공사를 부르지 않는다. 개발자 PC의 환경변수·backend/.env에 API 키가 있어도 비우고,
 * 분석은 FakeLlmClients(가짜 모델)로 돌린다. 다시 부르기 전 기다리는 시간도 없앤다.
 */
public class TestLlm implements ApplicationContextInitializer<ConfigurableApplicationContext> {

	@Override
	public void initialize(ConfigurableApplicationContext context) {
		context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("testLlm", Map.of(
				"app.llm.openai.api-key", "",
				"app.llm.anthropic.api-key", "",
				"app.llm.gemini.api-key", "",
				"app.llm.retry-backoff", "0ms")));
	}
}
