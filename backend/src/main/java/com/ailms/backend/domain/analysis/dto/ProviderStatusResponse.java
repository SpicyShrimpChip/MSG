package com.ailms.backend.domain.analysis.dto;

import io.swagger.v3.oas.annotations.media.Schema;

// AI 제공사 연결 상태. 키 값은 내보내지 않는다
public record ProviderStatusResponse(
		@Schema(allowableValues = {"gpt", "claude", "gemini"}) String provider,
		@Schema(description = "설정된 모델 이름. 미설정이면 null") String model,
		@Schema(description = "API 키와 모델 이름이 모두 있어야 true. false면 분석에서 부르지 않는다") boolean configured) {
}
