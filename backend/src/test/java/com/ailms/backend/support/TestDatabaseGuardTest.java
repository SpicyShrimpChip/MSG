package com.ailms.backend.support;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TestDatabaseGuardTest {

	@ParameterizedTest
	@ValueSource(strings = {
			"jdbc:h2:mem:lms;MODE=PostgreSQL",
			"jdbc:postgresql://localhost:5432/msg_test",
			"jdbc:postgresql://127.0.0.1:5432/msg_test?sslmode=require"
	})
	void H2_메모리_DB와_test_DB에서는_실행한다(String url) {
		assertThatCode(() -> TestDatabaseGuard.check(url)).doesNotThrowAnyException();
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"jdbc:postgresql://localhost:5432/msg?sslmode=require",
			"jdbc:postgresql://localhost:5432/msg",
			"jdbc:postgresql://localhost:5432/msg_testing",
			"jdbc:postgresql://localhost:5432/",
			""
	})
	void 서비스_DB나_알_수_없는_DB에서는_멈춘다(String url) {
		assertThatThrownBy(() -> TestDatabaseGuard.check(url))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("_test로 끝나는 DB");
	}
}
