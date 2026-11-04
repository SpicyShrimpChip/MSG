package com.ailms.backend.domain.quiz.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ailms.backend.domain.quiz.entity.QuestionType;
import com.ailms.backend.domain.quiz.entity.QuizMode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class QuizTypePlanTest {

	@Test
	void 고른_유형은_모두_한_문제_이상_들어가고_남는_문제는_객관식부터_더한다() {
		Map<QuestionType, Integer> plan = QuizTypePlan.plan(QuizMode.EXAM_PREP,
				List.of(QuestionType.ESSAY, QuestionType.SHORT_ANSWER, QuestionType.MULTIPLE_CHOICE), 5);

		assertThat(plan).containsExactly(Map.entry(QuestionType.MULTIPLE_CHOICE, 2),
				Map.entry(QuestionType.SHORT_ANSWER, 2), Map.entry(QuestionType.ESSAY, 1));
	}

	@Test
	void 업무_활용은_서술형부터_더한다() {
		Map<QuestionType, Integer> plan = QuizTypePlan.plan(QuizMode.WORK,
				List.of(QuestionType.MULTIPLE_CHOICE, QuestionType.ESSAY), 3);

		assertThat(plan).containsExactly(Map.entry(QuestionType.ESSAY, 2), Map.entry(QuestionType.MULTIPLE_CHOICE, 1));
	}

	@Test
	void 한_유형만_고르면_모두_그_유형() {
		assertThat(QuizTypePlan.plan(QuizMode.SELF_DEVELOPMENT, List.of(QuestionType.SHORT_ANSWER), 4))
				.containsExactly(Map.entry(QuestionType.SHORT_ANSWER, 4));
	}

	@Test
	void 문제_수가_유형_수보다_적으면_거절() {
		assertThatThrownBy(() -> QuizTypePlan.plan(QuizMode.EXAM_PREP, List.of(QuestionType.values()), 2))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
