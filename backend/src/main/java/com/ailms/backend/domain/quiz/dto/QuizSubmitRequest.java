package com.ailms.backend.domain.quiz.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record QuizSubmitRequest(
		@Schema(description = "문제별 답. 빠진 문제는 고르지 않은 것(-1)으로 채점한다")
		@NotNull @Size(max = QuizSubmitRequest.ANSWERS_MAX) List<@Valid @NotNull QuizAnswerRequest> answers) {

	public static final int ANSWERS_MAX = 200;
}
