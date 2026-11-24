package com.msg.backend.domain.quiz.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.msg.backend.domain.quiz.application.QuizGrader.Submitted;
import com.msg.backend.domain.quiz.dto.QuizAnswerRequest;
import com.msg.backend.domain.quiz.entity.QuizQuestion;
import com.msg.backend.global.error.BusinessException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class QuizGraderTest {

	// 문제 10번(정답 2), 20번(정답 0), 30번(정답 1). 보기는 모두 세 개
	private final List<QuizQuestion> questions = List.of(question(10L, 0, 2), question(20L, 1, 0), question(30L, 2, 1));

	@Test
	void 정답_번호와_같으면_맞고_문제_순서대로_돌려준다() {
		List<Submitted> submitted = QuizGrader.collect(questions,
				List.of(answer(30L, 1), answer(10L, 2), answer(20L, 2)));

		assertThat(submitted).extracting(s -> s.question().getId()).containsExactly(10L, 20L, 30L);
		assertThat(submitted).extracting(Submitted::choiceIndex).containsExactly(2, 2, 1);
		assertThat(submitted).extracting(Submitted::multipleChoiceCorrect).containsExactly(true, false, true);
	}

	@Test
	void 빠진_문제와_고르지_않은_문제는_틀림이다() {
		List<Submitted> submitted = QuizGrader.collect(questions, List.of(answer(10L, QuizGrader.NO_ANSWER)));

		assertThat(submitted).extracting(Submitted::choiceIndex).containsExactly(-1, -1, -1);
		assertThat(submitted).extracting(Submitted::multipleChoiceCorrect).containsExactly(false, false, false);
	}

	@Test
	void 없는_문제_같은_문제_두_번_범위_밖_번호는_거절한다() {
		assertThatThrownBy(() -> QuizGrader.collect(questions, List.of(answer(99L, 0))))
				.isInstanceOf(BusinessException.class);
		assertThatThrownBy(() -> QuizGrader.collect(questions, List.of(answer(10L, 2), answer(10L, 2))))
				.isInstanceOf(BusinessException.class);
		assertThatThrownBy(() -> QuizGrader.collect(questions, List.of(answer(10L, 3))))
				.isInstanceOf(BusinessException.class);
		assertThatThrownBy(() -> QuizGrader.collect(questions, List.of(answer(10L, -2))))
				.isInstanceOf(BusinessException.class);
	}

	private static QuizQuestion question(Long id, int sortOrder, int correctChoiceIndex) {
		QuizQuestion question = QuizQuestion.create(1L, sortOrder, "문제", List.of("A", "B", "C"), correctChoiceIndex,
				null, List.of());
		ReflectionTestUtils.setField(question, "id", id);
		return question;
	}

	private static QuizAnswerRequest answer(Long questionId, int choiceIndex) {
		return new QuizAnswerRequest(questionId, choiceIndex);
	}
}
