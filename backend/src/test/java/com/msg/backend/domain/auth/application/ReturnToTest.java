package com.msg.backend.domain.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

// 로그인 뒤 돌아갈 주소는 우리 사이트 안의 경로만 받는다 (다른 사이트로 보내는 열린 리다이렉트 방지)
class ReturnToTest {

	@ParameterizedTest
	@ValueSource(strings = {"/analysis", "/quizzes/3?tab=a", "/"})
	void 사이트_안의_경로는_그대로_둔다(String path) {
		assertThat(ReturnTo.sanitize(path)).isEqualTo(path);
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {"//evil.com", "/\\evil.com", "https://evil.com", "javascript:alert(1)", "/a\nb", "/a\\b",
			"analysis", " /analysis", "/a\tb"})
	void 다른_사이트나_이상한_값은_첫_화면으로_바꾼다(String raw) {
		assertThat(ReturnTo.sanitize(raw)).isEqualTo("/");
	}

	@Test
	void 너무_길면_첫_화면으로_바꾼다() {
		assertThat(ReturnTo.sanitize("/" + "a".repeat(199))).hasSize(200);
		assertThat(ReturnTo.sanitize("/" + "a".repeat(200))).isEqualTo("/");
	}
}
