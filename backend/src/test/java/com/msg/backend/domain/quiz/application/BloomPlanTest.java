package com.msg.backend.domain.quiz.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.msg.backend.domain.quiz.entity.BloomLevel;
import com.msg.backend.domain.quiz.entity.QuestionType;
import com.msg.backend.domain.quiz.entity.QuizMode;
import java.util.List;
import org.junit.jupiter.api.Test;

class BloomPlanTest {

	private static final List<QuestionType> ALL = List.of(QuestionType.MULTIPLE_CHOICE, QuestionType.SHORT_ANSWER,
			QuestionType.ESSAY);

	@Test
	void 학습_목적마다_평가하려는_인지_수준이_다르다() {
		assertThat(BloomPlan.targets(QuizMode.EXAM_PREP, List.of(), ALL)).containsExactly(BloomLevel.REMEMBER,
				BloomLevel.UNDERSTAND, BloomLevel.APPLY, BloomLevel.ANALYZE);
		assertThat(BloomPlan.targets(QuizMode.SELF_DEVELOPMENT, null, ALL)).containsExactly(BloomLevel.UNDERSTAND,
				BloomLevel.APPLY, BloomLevel.ANALYZE, BloomLevel.EVALUATE);
		assertThat(BloomPlan.targets(QuizMode.WORK, List.of(), ALL)).containsExactly(BloomLevel.APPLY,
				BloomLevel.ANALYZE, BloomLevel.EVALUATE, BloomLevel.CREATE);
	}

	@Test
	void 고른_수준을_낮은_수준부터_쓰고_서술형이_없으면_창조를_뺀다() {
		assertThat(BloomPlan.targets(QuizMode.EXAM_PREP, List.of(BloomLevel.EVALUATE, BloomLevel.REMEMBER), ALL))
				.containsExactly(BloomLevel.REMEMBER, BloomLevel.EVALUATE);
		assertThat(BloomPlan.targets(QuizMode.WORK, List.of(), List.of(QuestionType.MULTIPLE_CHOICE)))
				.doesNotContain(BloomLevel.CREATE);
		assertThatThrownBy(() -> BloomPlan.targets(QuizMode.WORK, List.of(BloomLevel.CREATE),
				List.of(QuestionType.SHORT_ANSWER))).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void 창조만_고르고_서술형이_아닌_유형을_섞으면_그_문제에_쓸_수준이_없어_거절한다() {
		// 객관식은 창조로 낼 수 없으므로 어떤 답이 와도 검증을 통과하지 못한다 (모델 호출만 쓰고 502)
		assertThatThrownBy(() -> BloomPlan.targets(QuizMode.WORK, List.of(BloomLevel.CREATE),
				List.of(QuestionType.MULTIPLE_CHOICE, QuestionType.ESSAY))).isInstanceOf(IllegalArgumentException.class)
				.hasMessage(BloomPlan.CREATE_ONLY_WITH_OTHER_TYPES_MESSAGE);
		assertThat(BloomPlan.targets(QuizMode.WORK, List.of(BloomLevel.CREATE, BloomLevel.APPLY),
				List.of(QuestionType.MULTIPLE_CHOICE, QuestionType.ESSAY))).containsExactly(BloomLevel.APPLY,
						BloomLevel.CREATE);
		assertThat(BloomPlan.targets(QuizMode.WORK, List.of(BloomLevel.CREATE), List.of(QuestionType.ESSAY)))
				.containsExactly(BloomLevel.CREATE);
	}

	@Test
	void 다뤄야_하는_서로_다른_수준_수와_저장_형식() {
		List<BloomLevel> four = BloomPlan.defaults(QuizMode.EXAM_PREP);
		assertThat(BloomPlan.requiredDistinct(four, 2)).isEqualTo(2);
		assertThat(BloomPlan.requiredDistinct(four, 10)).isEqualTo(4);
		assertThat(BloomPlan.join(four)).isEqualTo("REMEMBER,UNDERSTAND,APPLY,ANALYZE");
		assertThat(BloomPlan.split("REMEMBER, APPLY,UNKNOWN,APPLY")).containsExactly(BloomLevel.REMEMBER,
				BloomLevel.APPLY);
		assertThat(BloomPlan.split(null)).isEmpty();
	}
}
