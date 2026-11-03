package com.ailms.backend.domain.quiz.entity;

import com.ailms.backend.global.common.ApiEnumValues;
import java.util.Optional;

/**
 * 문제 유형. 객관식은 코드로, 주관식은 허용 답 일치(코드) 후 필요하면 AI로, 서술형은 채점 기준별 AI 판단 + 코드 합산으로 채점한다.
 */
public enum QuestionType {
	MULTIPLE_CHOICE,
	SHORT_ANSWER,
	ESSAY;

	public String apiValue() {
		return ApiEnumValues.of(this);
	}

	public static Optional<QuestionType> fromApiValue(String value) {
		return ApiEnumValues.parse(QuestionType.class, value);
	}
}
