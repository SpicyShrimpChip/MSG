package com.ailms.backend.domain.quiz.entity;

import com.ailms.backend.global.common.ApiEnumValues;
import java.util.Optional;

// 퀴즈 만들기 요청의 상태
public enum QuizGenerationStatus {
	RUNNING,
	SUCCEEDED,
	FAILED,
	/** 마감 시각이 지나 끝난 것으로 본 요청 (늦게 온 모델 답은 저장하지 않는다) */
	TIMED_OUT;

	public String apiValue() {
		return ApiEnumValues.of(this);
	}

	public static Optional<QuizGenerationStatus> fromApiValue(String value) {
		return ApiEnumValues.parse(QuizGenerationStatus.class, value);
	}
}
