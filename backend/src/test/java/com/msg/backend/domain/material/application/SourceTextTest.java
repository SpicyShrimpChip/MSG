package com.msg.backend.domain.material.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.msg.backend.global.common.VerificationStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

class SourceTextTest {

	private static final String PDF = "[p. 1]\n강의 소개와 평가 방법\n\n[p. 2]\n공개키 암호는 공개키와 개인키 한 쌍을 쓴다.\n"
			+ "공개키로 암호화하면 개인키로만 풀 수 있다.\n\n[p. 3]\nVLAN은 브로드캐스트 도메인을 나눈다.";

	@Test
	void 쪽_표시마다_부분으로_나누고_표시가_없으면_문단마다_번호를_붙인다() {
		assertThat(SourceText.parse(PDF).sections()).extracting(SourceText.Section::location)
				.containsExactly("p. 1", "p. 2", "p. 3");
		assertThat(SourceText.parse("첫 문단\n\n둘째 문단\n이어짐\n\n\n셋째").sections())
				.extracting(SourceText.Section::location).containsExactly("문단 1", "문단 2", "문단 3");
		assertThat(SourceText.parse("  ").isEmpty()).isTrue();
	}

	@Test
	void 개념이_나오는_부분만_고르고_없으면_앞에서부터_채운다() {
		SourceText source = SourceText.parse(PDF);
		SourceText.Selection selected = source.select(List.of("공개키 암호"), 12_000);
		assertThat(selected.locations()).containsExactly("p. 2");
		assertThat(selected.filtered()).isTrue();
		assertThat(selected.text()).startsWith("[p. 2]").doesNotContain("강의 소개", "VLAN");

		SourceText.Selection fallback = source.select(List.of("OSPF"), 12_000);
		assertThat(fallback.filtered()).isFalse();
		assertThat(fallback.locations()).containsExactly("p. 1", "p. 2", "p. 3");

		// 예산이 작으면 점수가 높은 부분부터, 원래 순서로 돌려준다
		SourceText.Selection small = source.select(List.of("공개키", "VLAN"), 60);
		assertThat(small.locations()).containsExactly("p. 2");
		assertThat(small.truncated()).isTrue();
	}

	@Test
	void 영문_용어는_낱말_경계에서만_세고_한글_용어는_조사가_붙어도_센다() {
		assertThat(SourceText.occurrences("Office hours and contacts", "RSA")).isZero();
		assertThat(SourceText.occurrences("RSA is used. rsa keys", "RSA")).isEqualTo(2);
		assertThat(SourceText.occurrences("TCP / IP and TCP/IP", "TCP/IP")).isEqualTo(1);
		assertThat(SourceText.occurrences("공개키암호를 쓰면 공개키 암호는", "공개키 암호")).isEqualTo(2);
		assertThat(SourceText.parse(PDF).missingTerms(List.of("VLAN", "방화벽"))).containsExactly("방화벽");
	}

	@Test
	void 인용을_원문에서_확인하고_위치는_서버가_찾는다() {
		String sent = SourceText.parse(PDF).select(List.of("공개키", "VLAN"), 12_000).text();

		SourceText.QuoteCheck exact = SourceText.check(sent, "공개키로 암호화하면  개인키로만 풀 수 있다");
		assertThat(exact.status()).isEqualTo(VerificationStatus.VERIFIED);
		assertThat(exact.location()).isEqualTo("p. 2");
		assertThat(exact.sectionText()).contains("한 쌍");

		// PDF 줄바꿈이 끼어도 같은 문장으로 본다
		assertThat(SourceText.check(sent, "한 쌍을 쓴다. 공개키로").status()).isEqualTo(VerificationStatus.VERIFIED);
		assertThat(SourceText.check(sent, "VLAN은 브로드캐스트").location()).isEqualTo("p. 3");

		SourceText.QuoteCheck partial = SourceText.check(sent, "공개키와 개인키 한 쌍을 반드시 함께 쓴다");
		assertThat(partial.status()).isEqualTo(VerificationStatus.PARTIAL);
		assertThat(partial.location()).isEqualTo("p. 2");

		assertThat(SourceText.check(sent, "양자 컴퓨터로 공개키를 깬다는 내용이 있다").status())
				.isEqualTo(VerificationStatus.FAILED);
		assertThat(SourceText.check(sent, null).status()).isEqualTo(VerificationStatus.INSUFFICIENT);
		assertThat(SourceText.check(sent, "키").status()).isEqualTo(VerificationStatus.INSUFFICIENT);
		// 보내지 않은 부분의 문장은 원문에 있어도 확인되지 않는다 (지정한 자료 부분을 실제로 썼는지)
		String onlyP2 = SourceText.parse(PDF).select(List.of("공개키"), 12_000).text();
		assertThat(SourceText.check(onlyP2, "VLAN은 브로드캐스트 도메인을 나눈다").status())
				.isEqualTo(VerificationStatus.FAILED);
	}

	@Test
	void 정답_표현이_근거에_나오는지는_참고_신호로_본다() {
		assertThat(SourceText.mentions("공개키와 개인키 한 쌍", "개인 키")).isTrue();
		assertThat(SourceText.mentions("공개키와 개인키 한 쌍", "해시")).isFalse();
		assertThat(SourceText.mentions("abc", "a")).isFalse();
	}
}
