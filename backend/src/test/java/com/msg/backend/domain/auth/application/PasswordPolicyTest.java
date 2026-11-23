package com.msg.backend.domain.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

// 프론트 frontend/src/utils/password.ts와 같은 규칙인지 확인한다
class PasswordPolicyTest {

	@ParameterizedTest
	@ValueSource(strings = {"abcd123!", "Password1@", "a1~aaaaa", "한글도됨abc1!"})
	void 네_가지_조건을_모두_지키면_통과한다(String password) {
		assertThat(PasswordPolicy.findViolation(password)).isEmpty();
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {
			"abc12!|비밀번호는 8자 이상이어야 해요.",
			"12345678!|비밀번호에 영문을 포함해 주세요.",
			"abcdefgh!|비밀번호에 숫자를 포함해 주세요.",
			"abcdefgh1|비밀번호에 특수문자(!, @, # 등)를 포함해 주세요.",
			// 한글은 특수문자로 치지 않는다
			"abcdefg1한|비밀번호에 특수문자(!, @, # 등)를 포함해 주세요."
	})
	void 지키지_않은_첫_번째_규칙을_알려_준다(String password, String expected) {
		assertThat(PasswordPolicy.findViolation(password)).contains(expected);
	}

	@ParameterizedTest
	@ValueSource(ints = {72, 73})
	void BCrypt가_다룰_수_있는_72바이트까지만_받는다(int bytes) {
		String password = "a1!" + "a".repeat(bytes - 3);
		boolean rejected = PasswordPolicy.findViolation(password).isPresent();
		assertThat(rejected).isEqualTo(bytes > PasswordPolicy.MAX_BYTES);
	}
}
