package com.ailms.backend.domain.quiz.dto;

import com.ailms.backend.domain.quiz.entity.Quiz;
import com.ailms.backend.domain.quiz.entity.QuizQuestion;
import java.util.List;

/**
 * 풀기 화면용 퀴즈 (frontend/src/types/quiz.ts의 Quiz). 정답 번호와 해설은 싣지 않는다. 필드 자체가 없어서
 * 실수로 내보낼 수 없다. 정답은 제출 뒤 QuizResultResponse로만 나간다.
 */
public record QuizResponse(Long id, String title, Long courseId, Long materialId, List<Question> questions) {

	public record Question(Long id, String text, List<String> choices) {
	}

	public static QuizResponse of(Quiz quiz, List<QuizQuestion> questions) {
		return new QuizResponse(quiz.getId(), quiz.getTitle(), quiz.getCourseId(), quiz.getMaterialId(),
				questions.stream()
						.map(q -> new Question(q.getId(), q.getContent(), List.copyOf(q.getChoices())))
						.toList());
	}
}
