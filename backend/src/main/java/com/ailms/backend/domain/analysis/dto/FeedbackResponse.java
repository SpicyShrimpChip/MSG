package com.ailms.backend.domain.analysis.dto;

import com.ailms.backend.domain.analysis.entity.ConceptFeedback;
import com.ailms.backend.domain.analysis.entity.FeedbackType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

// 사용자가 남긴 학습 피드백. AI 판단과 숙련도는 바꾸지 않는다
public record FeedbackResponse(
		Long id,
		Long courseId,
		String concept,
		@Schema(allowableValues = {"already_know", "want_to_study"}) String feedback,
		String comment,
		Long analysisRunId,
		LocalDateTime updatedAt,
		@Schema(allowableValues = {"confirm_quiz", "prioritize_study"},
				description = "confirm_quiz: 확인 퀴즈로 검증을 권함 / prioritize_study: 추천 학습 순서를 앞당김") String nextAction) {

	public static FeedbackResponse from(ConceptFeedback feedback) {
		return new FeedbackResponse(feedback.getId(), feedback.getCourseId(), feedback.getConceptName(),
				feedback.getFeedback().apiValue(), feedback.getComment(), feedback.getAnalysisRunId(),
				feedback.getUpdatedAt(),
				feedback.getFeedback() == FeedbackType.ALREADY_KNOW ? "confirm_quiz" : "prioritize_study");
	}
}
