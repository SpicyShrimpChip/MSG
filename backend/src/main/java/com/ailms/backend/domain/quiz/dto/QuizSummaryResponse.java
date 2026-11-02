package com.ailms.backend.domain.quiz.dto;

import com.ailms.backend.domain.quiz.entity.Quiz;
import com.ailms.backend.domain.quiz.entity.QuizSubmission;
import io.swagger.v3.oas.annotations.media.Schema;

// 내 퀴즈 목록 한 줄 (frontend/src/types/quiz.ts의 QuizSummary)
public record QuizSummaryResponse(
		Long id,
		String title,
		Long courseId,
		@Schema(description = "이 퀴즈를 만든 자료. 자료 없이 만들었으면 null") Long materialId,
		int questionCount,
		@Schema(description = "가장 최근 제출의 맞힌 수. 아직 안 풀었으면 null") Integer lastCorrectCount) {

	public static QuizSummaryResponse of(Quiz quiz, int questionCount, QuizSubmission latest) {
		return new QuizSummaryResponse(quiz.getId(), quiz.getTitle(), quiz.getCourseId(), quiz.getMaterialId(),
				questionCount, latest == null ? null : latest.getCorrectCount());
	}
}
