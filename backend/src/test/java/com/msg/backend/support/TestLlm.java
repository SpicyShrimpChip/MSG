package com.msg.backend.support;

import java.util.Map;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.test.context.support.TestPropertySourceUtils;

/**
 * 통합 테스트는 실제 AI 제공사를 부르지 않는다. 개발자 PC의 환경변수·backend/.env에 API 키가 있어도 비우고,
 * 분석은 FakeLlmClients(가짜 모델)로 돌린다. 다시 부르기 전 기다리는 시간도 없앤다.
 * 재가입 제한 키도 비운다(개발자 PC의 backend/.env에 키가 있으면 같은 이메일로 다시 가입하는 테스트가 409로 깨진다).
 * 재가입 제한 테스트는 @TestPropertySource로 키를 넣고, 그 값이 이긴다.
 */
public class TestLlm implements ApplicationContextInitializer<ConfigurableApplicationContext> {

	private static final String INLINED = TestPropertySourceUtils.INLINED_PROPERTIES_PROPERTY_SOURCE_NAME;

	@Override
	public void initialize(ConfigurableApplicationContext context) {
		context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("testLlm", Map.of(
				"app.llm.openai.api-key", "",
				"app.llm.anthropic.api-key", "",
				"app.llm.gemini.api-key", "",
				"app.llm.retry-backoff", "0ms",
				// 개발자 PC에 Turnstile 비밀 키가 있어도 통합 테스트의 가입은 확인하지 않는다 (확인은 SignupBotCheckTest)
				"app.turnstile.secret-key", "",
				// 테스트 전체가 같은 날짜 줄을 같이 쓰므로 상한에 닿지 않게 크게 둔다 (상한 테스트는 줄을 직접 채운다)
				"app.ai-daily-limit.calls", "1000000")));
		MutablePropertySources sources = context.getEnvironment().getPropertySources();
		MapPropertySource secrets = new MapPropertySource("testSecrets", Map.of("app.rejoin.hash-secret", ""));
		if (sources.contains(INLINED)) {
			sources.addAfter(INLINED, secrets);
		} else {
			sources.addFirst(secrets);
		}
	}
}
