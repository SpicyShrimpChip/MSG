package com.msg.backend.domain.material.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

// 자료 요약본 만들기. 자료 본문(서버가 고른 부분)을 외부 AI로 보내므로 이번 요청의 확인이 필요하다
public record StudySummaryRequest(
		@Schema(description = "학습 목적: exam_prep / self_development / work") @NotBlank(message = "학습 목적을 골라 주세요.") String purpose,
		@Schema(description = "자료 본문을 외부 AI로 보내는 데 대한 이번 요청의 확인 (필수)") Boolean materialContentConsent,
		@Schema(description = "같은 자료·같은 목적의 요약이 있어도 새로 만들기. 없으면 false(저장한 요약을 다시 쓴다)") Boolean regenerate) {
}
