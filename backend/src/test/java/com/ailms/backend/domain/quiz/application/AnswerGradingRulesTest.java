package com.ailms.backend.domain.quiz.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.ailms.backend.domain.quiz.entity.RubricLevel;
import java.util.List;
import org.junit.jupiter.api.Test;

// AI 판단을 점수·상태로 바꾸는 코드 규칙
class AnswerGradingRulesTest {

	@Test
	void 기준_점수는_충족_배점_부분_절반_내림_미충족_0() {
		assertThat(AnswerGradingService.points(RubricLevel.MET, 5)).isEqualTo(5);
		assertThat(AnswerGradingService.points(RubricLevel.PARTIAL, 5)).isEqualTo(2);
		assertThat(AnswerGradingService.points(RubricLevel.PARTIAL, 2)).isEqualTo(1);
		assertThat(AnswerGradingService.points(RubricLevel.NOT_MET, 5)).isZero();
	}

	@Test
	void 과반은_절반을_넘어야_하고_둘이면_둘_다_같아야_한다() {
		assertThat(AnswerGradingService.majority(List.of("MET", "MET", "PARTIAL"))).isEqualTo("MET");
		assertThat(AnswerGradingService.majority(List.of("MET", "PARTIAL", "NOT_MET"))).isNull();
		assertThat(AnswerGradingService.majority(List.of("MET", "PARTIAL"))).isNull();
		assertThat(AnswerGradingService.majority(List.of("MET", "MET"))).isEqualTo("MET");
		assertThat(AnswerGradingService.majority(List.of("PARTIAL"))).isEqualTo("PARTIAL");
	}
}
