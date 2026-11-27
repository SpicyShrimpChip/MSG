package com.msg.backend.domain.analysis.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

// 산식 버전 = 사람이 올리는 앞 번호 + 숙련도 숫자 설정의 지문. 숫자만 바꿔도 버전이 달라져 앞 기록과 단계를 비교하지 않는다
class MasteryPropertiesTest {

	private static MasteryProperties properties(String formulaVersion, double quizWeight) {
		return new MasteryProperties(formulaVersion, quizWeight, 0.5, 1.0, 0.3, 0.0, 3.0, 0.4, 0.7);
	}

	@Test
	void 같은_설정이면_같은_버전이다() {
		assertThat(properties("2", 1.0).effectiveFormulaVersion())
				.isEqualTo(properties("2", 1.0).effectiveFormulaVersion());
	}

	@Test
	void 숫자_설정을_하나만_바꿔도_버전이_달라진다() {
		assertThat(properties("2", 1.2).effectiveFormulaVersion())
				.isNotEqualTo(properties("2", 1.0).effectiveFormulaVersion());
		assertThat(new MasteryProperties("2", 1.0, 0.5, 1.0, 0.3, 0.0, 3.0, 0.4, 0.75).effectiveFormulaVersion())
				.isNotEqualTo(properties("2", 1.0).effectiveFormulaVersion());
	}

	@Test
	void 앞_번호는_그대로_두고_지문_8자리를_붙인다() {
		String version = properties("2", 1.0).effectiveFormulaVersion();

		assertThat(version).matches("2-[0-9a-f]{8}");
		assertThat(version.length()).isLessThanOrEqualTo(MasteryProperties.MAX_FORMULA_VERSION_LENGTH);
	}

	@Test
	void 앞_번호가_비었거나_하이픈이_있거나_너무_길면_시작하지_않는다() {
		assertThatThrownBy(() -> properties(null, 1.0)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> properties(" ", 1.0)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> properties("2-1", 1.0)).isInstanceOf(IllegalArgumentException.class);
		// 앞 번호 12자 + "-" + 8자 = 21자 > 20자 (concept_mastery.formula_version VARCHAR(20))
		assertThatThrownBy(() -> properties("123456789012", 1.0)).isInstanceOf(IllegalArgumentException.class);
		assertThat(properties("12345678901", 1.0).effectiveFormulaVersion()).hasSize(20);
	}
}
