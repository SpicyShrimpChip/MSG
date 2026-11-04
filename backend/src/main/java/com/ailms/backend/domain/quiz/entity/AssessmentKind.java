package com.ailms.backend.domain.quiz.entity;

import com.ailms.backend.global.common.ApiEnumValues;
import java.util.Optional;

/**
 * 학습자료 기반 평가 종류. 사후평가는 같은 자료의 사전평가와 같은 개념·유형·문항 수로 만들어 비교한다.
 */
public enum AssessmentKind {
	/** 사전평가: 학습 전 이해 수준과 부족한 개념 찾기 */
	PRE,
	/** 사후평가: 학습 뒤 같은 개념으로 다시 평가해 향상 확인 */
	POST;

	public String apiValue() {
		return ApiEnumValues.of(this);
	}

	public static Optional<AssessmentKind> fromApiValue(String value) {
		return ApiEnumValues.parse(AssessmentKind.class, value);
	}
}
