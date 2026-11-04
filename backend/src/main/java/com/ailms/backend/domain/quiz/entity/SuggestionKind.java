package com.ailms.backend.domain.quiz.entity;

import com.ailms.backend.global.common.ApiEnumValues;
import java.util.Optional;

// AI가 제안한 다음 학습 개념의 종류 (자기개발 모드). 실제 학습 결과가 아니라 제안이다
public enum SuggestionKind {
	/** 다음 단계 */
	NEXT,
	/** 보완할 선수 지식 */
	PREREQUISITE,
	/** 연관 분야 */
	RELATED;

	public String apiValue() {
		return ApiEnumValues.of(this);
	}

	public static Optional<SuggestionKind> fromApiValue(String value) {
		return ApiEnumValues.parse(SuggestionKind.class, value);
	}
}
