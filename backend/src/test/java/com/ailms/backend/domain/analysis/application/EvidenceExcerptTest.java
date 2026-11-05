package com.ailms.backend.domain.analysis.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import org.junit.jupiter.api.Test;

// 판단 근거로 보여 줄 메시지 앞부분: 마크다운 표시 걷기와 자르기
class EvidenceExcerptTest {

	@Test
	void 짧은_글은_그대로_둔다() {
		assertThat(EvidenceExcerpt.of("CBC는 IV를 쓰니까 오류가 전파 안 되는 거 맞지?"))
				.isEqualTo("CBC는 IV를 쓰니까 오류가 전파 안 되는 거 맞지?");
	}

	@Test
	void 굵게_코드_링크_표시를_걷어_낸다() {
		assertThat(EvidenceExcerpt.of("**ECB (맞음)** - `E(P, K)`는 [위키](https://example.com/ecb) 참고"))
				.isEqualTo("ECB (맞음) - E(P, K)는 위키 참고");
	}

	@Test
	void 줄_앞의_제목_목록_인용_기호를_걷고_줄을_이어_붙인다() {
		String content = "## 정리\n- 첫째\n1. 둘째\n> - 셋째\n---\n```java\nint x;\n```";
		assertThat(EvidenceExcerpt.of(content)).isEqualTo("정리 첫째 둘째 셋째 int x;");
	}

	@Test
	void 별표_밑줄_하나는_수식이라_그대로_둔다() {
		assertThat(EvidenceExcerpt.of("C_i = E(P_i, K)이고 3*5*2 = 30"))
				.isEqualTo("C_i = E(P_i, K)이고 3*5*2 = 30");
	}

	@Test
	void 앞부분만_떼어_짝이_잘린_굵게_표시도_지운다() {
		String content = "가".repeat(390) + " **굵게 시작한 뒤 400자 밖에서 끝나는 글" + "나".repeat(50) + "**";
		assertThat(EvidenceExcerpt.of(content)).doesNotContain("*").endsWith(EvidenceExcerpt.ELLIPSIS);
	}

	@Test
	void 긴_글은_단어_경계에서_자르고_말줄임표를_붙인다() {
		String content = "블록 암호 모드 ".repeat(30);
		String excerpt = EvidenceExcerpt.of(content);
		assertThat(excerpt).endsWith(EvidenceExcerpt.ELLIPSIS);
		assertThat(excerpt.length()).isLessThanOrEqualTo(EvidenceExcerpt.MAX_LENGTH + 1);
		String body = excerpt.substring(0, excerpt.length() - 1);
		assertThat(content).startsWith(body + " ");
	}

	@Test
	void 띄어쓰기가_없으면_최대_길이에서_자른다() {
		String excerpt = EvidenceExcerpt.of("가".repeat(300));
		assertThat(excerpt).isEqualTo("가".repeat(EvidenceExcerpt.MAX_LENGTH) + EvidenceExcerpt.ELLIPSIS);
	}

	@Test
	void 이모지를_반으로_자르지_않는다() {
		String content = "가".repeat(EvidenceExcerpt.MAX_LENGTH - 1) + "😀" + "나".repeat(10);
		String excerpt = EvidenceExcerpt.of(content);
		assertThat(excerpt).isEqualTo("가".repeat(EvidenceExcerpt.MAX_LENGTH - 1) + EvidenceExcerpt.ELLIPSIS);
	}

	@Test
	void 아주_긴_메시지도_바로_끝난다() {
		String content = "**".repeat(500_000) + "[".repeat(200_000);
		assertTimeoutPreemptively(Duration.ofSeconds(1), () -> EvidenceExcerpt.of(content));
	}

	@Test
	void 빈_글은_빈_글() {
		assertThat(EvidenceExcerpt.of("")).isEmpty();
		assertThat(EvidenceExcerpt.of(null)).isEmpty();
	}
}
