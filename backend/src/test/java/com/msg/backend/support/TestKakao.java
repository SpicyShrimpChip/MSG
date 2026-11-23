package com.msg.backend.support;

import java.util.Map;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.test.context.support.TestPropertySourceUtils;

/**
 * 통합 테스트의 카카오 로그인은 가짜 카카오 서버(FakeKakaoServer)로 돈다. 개발자 PC의 backend/.env에 실제 키가 있어도
 * 가짜 값으로 덮는다. 서버는 컨텍스트마다 하나 띄워 빈으로 등록하고(테스트에서 @Autowired), 컨텍스트가 닫히면 끈다.
 * 키가 없을 때의 동작은 SocialAuthUnavailableTest가 이 값을 비워서 확인한다.
 */
public class TestKakao implements ApplicationContextInitializer<ConfigurableApplicationContext> {

	public static final String CLIENT_ID = "test-client-id";
	public static final String CLIENT_SECRET = "test-client-secret";
	public static final String ADMIN_KEY = "test-admin";
	public static final String REDIRECT_URI = "http://localhost:5173/api/auth/kakao/callback";
	public static final String FRONTEND = "http://localhost:5173";
	private static final String INLINED = TestPropertySourceUtils.INLINED_PROPERTIES_PROPERTY_SOURCE_NAME;

	@Override
	public void initialize(ConfigurableApplicationContext context) {
		FakeKakaoServer server = FakeKakaoServer.start();
		context.getBeanFactory().registerSingleton("fakeKakaoServer", server);
		context.addApplicationListener(event -> {
			if (event instanceof ContextClosedEvent) {
				server.close();
			}
		});
		MutablePropertySources sources = context.getEnvironment().getPropertySources();
		MapPropertySource fake = new MapPropertySource("testKakao", Map.of(
				"app.oauth.kakao.client-id", CLIENT_ID,
				"app.oauth.kakao.client-secret", CLIENT_SECRET,
				"app.oauth.kakao.admin-key", ADMIN_KEY,
				"app.oauth.kakao.redirect-uri", REDIRECT_URI,
				"app.oauth.kakao.auth-base-url", server.baseUrl(),
				"app.oauth.kakao.api-base-url", server.baseUrl(),
				"app.oauth.kakao.timeout", "2s",
				"app.frontend.base-url", FRONTEND));
		// 테스트 클래스가 @TestPropertySource로 값을 바꾸면 그 값이 이기게 한다(그 밖의 설정·backend/.env보다는 앞)
		if (sources.contains(INLINED)) {
			sources.addAfter(INLINED, fake);
		} else {
			sources.addFirst(fake);
		}
	}
}
