package com.ailms.backend.domain.quiz.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ShortAnswerMatcherTest {

	@Test
	void 대소문자_띄어쓰기_하이픈_전각은_같게_본다() {
		List<String> accepted = List.of("PAT", "Port Address Translation");

		assertThat(ShortAnswerMatcher.matches("pat", accepted)).isTrue();
		assertThat(ShortAnswerMatcher.matches("  ＰＡＴ ", accepted)).isTrue();
		assertThat(ShortAnswerMatcher.matches("port-address translation.", accepted)).isTrue();
	}

	@Test
	void 여러_허용_답_중_하나와_같으면_맞다() {
		assertThat(ShortAnswerMatcher.matches("NAPT", List.of("PAT", "NAPT", "NAT 오버로드"))).isTrue();
		assertThat(ShortAnswerMatcher.matches("nat오버로드", List.of("PAT", "NAPT", "NAT 오버로드"))).isTrue();
	}

	@Test
	void 뜻이_바뀌는_기호는_지우지_않고_빈_답은_맞지_않다() {
		assertThat(ShortAnswerMatcher.matches("C", List.of("C++"))).isFalse();
		assertThat(ShortAnswerMatcher.matches("c++", List.of("C++"))).isTrue();
		assertThat(ShortAnswerMatcher.matches("  ", List.of(""))).isFalse();
	}

	@Test
	void 표기가_다른_답은_일치로_보지_않는다_의미_판단은_AI로_넘긴다() {
		assertThat(ShortAnswerMatcher.matches("포트 주소 변환", List.of("PAT"))).isFalse();
	}
}
