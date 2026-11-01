package com.ailms.backend.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

// AI 대화 분석 동의 바꾸기 (frontend/src/api/auth.api.ts updateAiConsent)
public record AiConsentRequest(
		@Schema(description = "true: 동의 / false: 철회 (가져온 대화를 함께 지운다)")
		@NotNull(message = "동의 여부를 골라 주세요.")
		Boolean aiAnalysis) {
}
