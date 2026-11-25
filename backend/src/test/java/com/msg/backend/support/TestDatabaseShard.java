package com.msg.backend.support;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;

/**
 * pgTest를 JVM 여러 개로 나눠 돌릴 때, JVM마다 다른 테스트 DB를 쓰게 한다.
 * 테스트는 매번 행을 지우므로 같은 DB를 함께 쓰면 서로의 데이터를 지운다.
 * build.gradle의 pgTest가 나눈 수(msg.test.db.shards)를 넘기면, Gradle이 JVM마다 붙이는 번호(org.gradle.test.worker)로
 * msg_test → msg_1_test ~ msg_N_test 중 하나를 고른다(번호는 한 작업 안에서 이어지므로 나머지가 겹치지 않는다).
 * 나눈 수가 없거나 1이면, 또는 H2이면 아무것도 바꾸지 않는다. 바꾼 이름도 _test로 끝나 TestDatabaseGuard를 통과한다.
 */
public class TestDatabaseShard implements ApplicationContextInitializer<ConfigurableApplicationContext> {

	static final String SHARDS_PROPERTY = "msg.test.db.shards";
	private static final Pattern TEST_DATABASE = Pattern.compile("^(jdbc:[a-z]+://[^/]+/)([^?;]+)_test(.*)$");

	@Override
	public void initialize(ConfigurableApplicationContext context) {
		int shards = Integer.getInteger(SHARDS_PROPERTY, 1);
		String worker = System.getProperty("org.gradle.test.worker");
		if (shards <= 1 || worker == null) {
			return;
		}
		String url = context.getEnvironment().getProperty("spring.datasource.url", "");
		String shardUrl = shardUrl(url, Long.parseLong(worker), shards);
		if (!shardUrl.equals(url)) {
			context.getEnvironment().getPropertySources()
					.addFirst(new MapPropertySource("testDatabaseShard", Map.of("spring.datasource.url", shardUrl)));
		}
	}

	static String shardUrl(String url, long worker, int shards) {
		Matcher matcher = TEST_DATABASE.matcher(url);
		if (!matcher.matches()) {
			return url;
		}
		long shard = Math.floorMod(worker, (long) shards) + 1;
		return matcher.group(1) + matcher.group(2) + "_" + shard + "_test" + matcher.group(3);
	}
}
