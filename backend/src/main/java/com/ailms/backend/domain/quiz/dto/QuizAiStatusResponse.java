package com.ailms.backend.domain.quiz.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 퀴즈·평가에 쓸 AI 상태. 키 값은 내보내지 않는다.
 * 문제 만들기: 설정 순서(gpt → claude → gemini)에서 설정된 첫 모델 하나. 실패하면 남은 시간 안에서 다음 모델.
 * AI 채점(주관식 의미 판단·서술형): 설정된 모델 모두. 하나뿐이면 그 모델 판단으로 채점하고 "모델 하나만 판단"으로 표시한다.
 */
public record QuizAiStatusResponse(
		@Schema(description = "설정된 모델이 하나라도 있는지") boolean available,
		@Schema(description = "문제를 만들 모델 (첫 번째). 없으면 null") Model generation,
		@Schema(description = "AI 채점에 쓸 모델들") List<Model> grading,
		@Schema(description = "화면에 보일 안내") String message,
		@Schema(description = "학습자료 본문을 외부 AI로 보낼 수 있는지 (운영 설정, 기본 꺼짐)") boolean materialContentEnabled) {

	public record Model(String provider, String model) {
	}
}
