package com.msg.backend.global.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TermMatchTest {

	// 프론트 frontend/src/utils/conversation/filter.test.ts의 표와 같다. 한쪽을 고치면 다른 쪽도 같이 고친다
	@ParameterizedTest(name = "[{0}] in \"{1}\" → {2}")
	@CsvSource(delimiter = '|', textBlock = """
			IP               | shipping address        | false
			IP               | IP 주소를 바꿨어요         | true
			IP               | TCP/IP 모델               | true
			IP               | IPv4 주소                 | false
			RSA              | RSA를 공부했어요           | true
			RSA              | office hours and        | false
			rsa              | RSA 암호                  | true
			RSA              | 공개키 rsa                | true
			VLAN             | VLAN 설정                 | true
			VLAN             | vlans are               | false
			TCP/IP           | tcp/ip 4계층              | true
			TCP/IP           | tcp / ip                | false
			C++              | C++로 짰어요               | true
			machine learning | Machine Learning 기초     | true
			machine learning | machinelearning         | false
			운영체제             | 운영체제론 수업               | true
			IP 주소            | VIP 주소 이벤트              | true
			교착 상태            | 교착상태                    | false
			""")
	void 영문_키워드는_낱말_경계에서만_한글_키워드는_부분_문자열로_찾는다(String keyword, String text, boolean found) {
		assertThat(TermMatch.keyword(keyword).foundIn(text.toLowerCase(Locale.ROOT))).isEqualTo(found);
	}

	@Test
	void 영문_용어_판단() {
		assertThat(TermMatch.isLatin(" TCP/IP ")).isTrue();
		assertThat(TermMatch.isLatin("Node.js")).isTrue();
		assertThat(TermMatch.isLatin("IP 주소")).isFalse();
		assertThat(TermMatch.isLatin("(RSA)")).isFalse();
	}

	@Test
	void 빈_키워드는_어디에도_없다() {
		assertThat(TermMatch.keyword("  ").foundIn("아무 글")).isFalse();
	}

	@Test
	void 긴_글에서도_느려지지_않는다() {
		String text = "a".repeat(1_000_000) + " ip";
		TermMatch.Keyword keyword = TermMatch.keyword("IP");
		assertTimeoutPreemptively(Duration.ofSeconds(2), () -> assertThat(keyword.foundIn(text)).isTrue());
	}
}
