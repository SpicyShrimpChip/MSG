package com.ailms.backend.domain.analysis.dto;

import com.ailms.backend.domain.analysis.entity.ConceptFeedback;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// 학습 모드 피드백 남기기·바꾸기 (과목·개념마다 하나)
public record FeedbackRequest(
		@Schema(description = "개념 이름 (과목 키워드 중 하나)")
		@NotBlank(message = "개념을 골라 주세요.")
		String concept,

		@Schema(allowableValues = {"already_know", "want_to_study"})
		@NotBlank(message = "피드백을 골라 주세요.")
		@Pattern(regexp = "^(already_know|want_to_study)$", message = "피드백을 골라 주세요.")
		String feedback,

		@Schema(description = "짧은 메모 (선택, 200자까지)")
		@Size(max = ConceptFeedback.COMMENT_MAX_LENGTH, message = "메모는 200자까지 쓸 수 있어요.")
		String comment,

		@Schema(description = "피드백을 남길 때 보고 있던 분석 id (선택)")
		Long analysisRunId) {
}
