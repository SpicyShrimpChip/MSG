package com.ailms.backend.domain.quiz.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record QuizAnswerRequest(
		@NotNull Long questionId,
		@Schema(description = "고른 보기 순서(0부터). 고르지 않았으면 -1") @NotNull @Min(-1) Integer choiceIndex) {
}
