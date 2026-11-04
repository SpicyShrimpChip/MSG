package com.ailms.backend.domain.quiz.entity;

import com.ailms.backend.global.common.ApiEnumValues;
import java.util.Optional;

// 채점 방법. 결과 화면에서 "코드 채점"과 "AI 판단"을 구분해 보여 준다
public enum GradingMethod {
	/** 객관식 정답 번호 비교 */
	CODE,
	/** 주관식 허용 답과 정규화 후 일치 */
	MATCH,
	/** AI 모델 판단 (주관식 의미 판단, 서술형 채점 기준) */
	AI;

	public String apiValue() {
		return ApiEnumValues.of(this);
	}

	public static Optional<GradingMethod> fromApiValue(String value) {
		return ApiEnumValues.parse(GradingMethod.class, value);
	}
}
