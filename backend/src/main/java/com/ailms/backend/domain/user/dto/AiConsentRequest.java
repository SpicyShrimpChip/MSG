package com.ailms.backend.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;

// 동의 바꾸기 (frontend/src/api/auth.api.ts updateAiConsent·updateAiQuizConsent). 보낸 항목만 바꾼다 (예전 요청 { aiAnalysis }도 그대로)
public record AiConsentRequest(
		@Schema(description = "AI 대화 분석 동의. true: 동의 / false: 철회 (가져온 대화를 함께 지운다). 없으면 그대로")
		Boolean aiAnalysis,
		@Schema(description = "AI 퀴즈 처리 동의. true: 동의 / false: 철회 (이미 만든 퀴즈·채점 결과는 남고 이후 AI 처리만 멈춘다). 없으면 그대로")
		Boolean aiQuiz) {

	@Schema(hidden = true)
	@AssertTrue(message = "동의 여부를 골라 주세요.")
	public boolean isAnySelected() {
		return aiAnalysis != null || aiQuiz != null;
	}
}
