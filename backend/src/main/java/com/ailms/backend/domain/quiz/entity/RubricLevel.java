package com.ailms.backend.domain.quiz.entity;

import com.ailms.backend.global.common.ApiEnumValues;
import java.util.Optional;

// 서술형 채점 기준 하나를 얼마나 채웠는지 (AI가 고르고, 점수는 코드가 정한다)
public enum RubricLevel {
	/** 충족: 배점 전부 */
	MET,
	/** 부분 충족: 배점의 절반(내림) */
	PARTIAL,
	/** 미충족: 0점 */
	NOT_MET;

	public String apiValue() {
		return ApiEnumValues.of(this);
	}

	public static Optional<RubricLevel> fromApiValue(String value) {
		return ApiEnumValues.parse(RubricLevel.class, value);
	}
}
