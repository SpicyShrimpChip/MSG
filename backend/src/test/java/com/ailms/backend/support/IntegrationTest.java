package com.ailms.backend.support;

import com.ailms.backend.global.storage.LocalFileStorage;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ContextConfiguration;

/**
 * 실제 설정으로 앱 전체를 띄우는 통합 테스트.
 * ./gradlew test는 H2, ./gradlew pgTest는 PostgreSQL(ai_lms_test)로 같은 테스트를 돌린다.
 * 모든 통합 테스트가 같은 설정을 써서 스프링 컨텍스트 하나를 같이 쓴다.
 * TestDatabaseGuard가 서비스 DB(ai_lms)에서는 실행되지 않게 막고,
 * TestStorage가 파일 저장소를 매번 새 임시 폴더로 바꾼다(개발용 업로드 폴더를 건드리지 않는다).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = {TestDatabaseGuard.class, TestStorage.class})
@Import(IntegrationTest.ClockConfig.class)
public @interface IntegrationTest {

	@TestConfiguration
	class ClockConfig {

		@Bean
		@Primary
		MutableClock mutableClock() {
			return new MutableClock();
		}

		// 서비스는 이 저장소를 받는다. 실패를 흉내 낼 때만 끼어들고 평소에는 임시 폴더에 그대로 저장한다
		@Bean
		@Primary
		FaultyFileStorage faultyFileStorage(LocalFileStorage localFileStorage) {
			return new FaultyFileStorage(localFileStorage);
		}
	}
}
