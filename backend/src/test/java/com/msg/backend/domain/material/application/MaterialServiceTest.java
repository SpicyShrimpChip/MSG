package com.msg.backend.domain.material.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MaterialServiceTest {

	@Test
	void 저장_용량_안내의_크기는_MB로_소수_첫째_자리까지_내려서_보여_준다() {
		long mb = 1024L * 1024;

		assertThat(MaterialService.megabytes(0)).isEqualTo("0MB");
		assertThat(MaterialService.megabytes(200 * mb)).isEqualTo("200MB");
		assertThat(MaterialService.megabytes(185 * mb + mb * 39 / 100)).isEqualTo("185.3MB");
		assertThat(MaterialService.megabytes(200 * mb - 1)).isEqualTo("199.9MB");
	}
}
