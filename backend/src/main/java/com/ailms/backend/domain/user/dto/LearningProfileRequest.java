package com.ailms.backend.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

// 학습 목적·관심 직무 바꾸기 (frontend/src/api/auth.api.ts updateLearningProfile). 세 값을 한 번에 바꾼다
public record LearningProfileRequest(
		@Schema(description = "exam_prep / self_development / work / undecided")
		@NotBlank(message = "학습 목적을 골라 주세요.")
		String learningPurpose,
		@Schema(description = "관심 직무. 고르지 않으면 null") String jobField,
		@Schema(description = "jobField가 other일 때 분야 이름 (30자까지)") String jobFieldOther) {
}
