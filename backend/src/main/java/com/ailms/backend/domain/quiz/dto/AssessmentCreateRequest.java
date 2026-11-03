package com.ailms.backend.domain.quiz.dto;

import com.ailms.backend.domain.quiz.entity.QuizGeneration;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 학습자료 기반 사전·사후평가 만들기 (frontend/src/types/assessment.ts의 AssessmentCreateInput).
 * 사후평가는 같은 자료의 최신 사전평가와 개념·유형·문항 수·난이도·모드를 서버가 맞추므로 그 값들은 무시한다.
 */
public record AssessmentCreateRequest(
		@NotBlank(message = "요청 키가 없어요. 화면을 새로 고쳐 주세요.")
		@Size(max = QuizGeneration.REQUEST_KEY_MAX_LENGTH, message = "요청 키가 너무 길어요.")
		String requestKey,
		@NotNull(message = "학습자료를 골라 주세요.") Long materialId,
		@Schema(description = "pre / post") @NotBlank(message = "사전평가인지 사후평가인지 골라 주세요.") String kind,
		@Schema(description = "학습 모드. 없으면 self_development (이해 확인)") String mode,
		@Schema(description = "문제 유형. 없으면 객관식 + 주관식") @Size(max = 3, message = "문제 유형을 다시 골라 주세요.")
		List<String> questionTypes,
		@Schema(description = "문제 수. 없으면 6") @Min(value = 1, message = "문제 수를 다시 골라 주세요.") Integer questionCount,
		@Schema(description = "easy / medium / hard. 없으면 medium") String difficulty,
		@Schema(description = "자료 본문(발췌)을 외부 AI로 보내 만들기") Boolean useMaterialContent,
		@Schema(description = "자료 본문 전송 확인 (useMaterialContent일 때 필수)") Boolean materialContentConsent) {
}
