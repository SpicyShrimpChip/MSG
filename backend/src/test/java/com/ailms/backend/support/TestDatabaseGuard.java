package com.ailms.backend.support;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * 통합 테스트가 서비스 DB(msg)를 지우지 않도록 막는다.
 * 테스트는 매번 회원·세션 행을 지우므로, H2 메모리 DB이거나 이름이 _test로 끝나는 DB가 아니면
 * 스프링이 뜨기 전에(Flyway·테스트가 DB에 닿기 전에) 실패시킨다.
 */
public class TestDatabaseGuard implements ApplicationContextInitializer<ConfigurableApplicationContext> {

	private static final Pattern DATABASE_NAME = Pattern.compile("^jdbc:[a-z]+://[^/]+/([^?;]+)");

	@Override
	public void initialize(ConfigurableApplicationContext context) {
		check(context.getEnvironment().getProperty("spring.datasource.url", ""));
	}

	static void check(String url) {
		if (url.startsWith("jdbc:h2:mem:")) {
			return;
		}
		String database = databaseName(url).orElse("(알 수 없음)");
		if (!database.endsWith("_test")) {
			throw new IllegalStateException("통합 테스트는 이름이 _test로 끝나는 DB에서만 실행한다. 서비스 DB를 지우지 않도록 멈춘다. "
					+ "현재 DB: " + database);
		}
	}

	static Optional<String> databaseName(String url) {
		Matcher matcher = DATABASE_NAME.matcher(url);
		return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
	}
}
