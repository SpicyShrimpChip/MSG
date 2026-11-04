package com.ailms.backend.domain.quiz.dto;

import com.ailms.backend.domain.quiz.entity.QuizAnswer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 문제 하나의 답. 객관식은 choiceIndex, 주관식·서술형은 text를 쓴다.
 * V6 요청({ questionId, choiceIndex })은 그대로 받는다.
 */
public record QuizAnswerRequest(
		@NotNull Long questionId,
		@Schema(description = "객관식: 고른 보기 순서(0부터), 안 골랐으면 -1. 주관식·서술형은 비우거나 -1") @Min(-1) Integer choiceIndex,
		@Schema(description = "주관식·서술형 답 (5000자까지). 객관식은 비운다")
		@Size(max = QuizAnswer.TEXT_MAX_LENGTH, message = "답은 5000자까지 쓸 수 있어요.") String text) {

	// 객관식 답 (V6 형태)
	public QuizAnswerRequest(Long questionId, Integer choiceIndex) {
		this(questionId, choiceIndex, null);
	}
}
