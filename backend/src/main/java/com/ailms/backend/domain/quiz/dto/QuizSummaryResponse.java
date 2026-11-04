package com.ailms.backend.domain.quiz.dto;

import com.ailms.backend.domain.quiz.entity.Quiz;
import com.ailms.backend.domain.quiz.entity.QuizSubmission;
import com.ailms.backend.global.common.ApiEnumValues;
import io.swagger.v3.oas.annotations.media.Schema;

// 내 퀴즈 목록 한 줄 (frontend/src/types/quiz.ts의 QuizSummary)
public record QuizSummaryResponse(
		Long id,
		String title,
		Long courseId,
		@Schema(description = "이 퀴즈와 연결한 자료. 없으면 null") Long materialId,
		int questionCount,
		@Schema(description = "가장 최근 제출의 완전히 맞힌 문제 수. 아직 안 풀었으면 null") Integer lastCorrectCount,
		@Schema(description = "exam_prep / self_development / work. V6 퀴즈는 null") String mode,
		String difficulty,
		@Schema(description = "AI가 만든 퀴즈인지") boolean aiGenerated,
		@Schema(description = "가장 최근 제출의 점수(채점이 끝난 답만). 안 풀었으면 null") Integer lastScore,
		@Schema(description = "가장 최근 제출의 만점(채점이 끝난 답만). 안 풀었으면 null") Integer lastMaxScore,
		@Schema(description = "가장 최근 제출에서 AI 채점을 기다리는 답 수. 안 풀었으면 null") Integer lastPendingCount,
		@Schema(description = "가장 최근 제출에서 AI 판단이 엇갈려 확인이 필요한 답 수. 안 풀었으면 null") Integer lastReviewCount,
		@Schema(description = "모의시험인지") boolean mockExam,
		@Schema(description = "사전·사후평가: pre / post. 일반 퀴즈는 null") String assessmentKind) {

	public static QuizSummaryResponse of(Quiz quiz, int questionCount, QuizSubmission latest) {
		return new QuizSummaryResponse(quiz.getId(), quiz.getTitle(), quiz.getCourseId(), quiz.getMaterialId(),
				questionCount, latest == null ? null : latest.getCorrectCount(), ApiEnumValues.of(quiz.getMode()),
				ApiEnumValues.of(quiz.getDifficulty()), quiz.getGeneratedProvider() != null,
				latest == null ? null : latest.getScore(), latest == null ? null : latest.getMaxScore(),
				latest == null ? null : latest.getPendingCount(), latest == null ? null : latest.getReviewCount(),
				quiz.isMockExam(), ApiEnumValues.of(quiz.getAssessmentKind()));
	}
}
