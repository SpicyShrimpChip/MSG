package com.ailms.backend.domain.quiz.entity;

import com.ailms.backend.global.common.ApiEnumValues;
import java.util.Optional;

// 퀴즈 난이도 (생성 지시에만 쓴다)
public enum QuizDifficulty {
	EASY,
	MEDIUM,
	HARD;

	public String apiValue() {
		return ApiEnumValues.of(this);
	}

	public static Optional<QuizDifficulty> fromApiValue(String value) {
		return ApiEnumValues.parse(QuizDifficulty.class, value);
	}
}
