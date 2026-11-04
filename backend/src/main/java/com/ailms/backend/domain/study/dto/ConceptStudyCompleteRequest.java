package com.ailms.backend.domain.study.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 개념 학습 완료. 같은 개념을 여러 번 보내도 기록은 하나다
public record ConceptStudyCompleteRequest(
		@Schema(description = "학습을 마친 개념의 conceptId (GET /api/materials/{id}/concepts의 값)")
		@NotBlank(message = "개념을 골라 주세요.") @Size(max = 40, message = "개념을 다시 골라 주세요.") String conceptId) {
}
