package com.ailms.backend.domain.quiz.entity;

import com.ailms.backend.global.common.ApiEnumValues;
import java.util.Optional;

/**
 * 답 하나의 채점 상태. 점수에는 GRADED만 넣고, 나머지는 결과 화면에 "채점 대기"·"확인 필요"로 따로 보여 준다.
 */
public enum GradingStatus {
	/** 채점 끝 */
	GRADED,
	/** AI 모델 판단이 엇갈리거나 확신하지 못해 사람이 다시 확인해야 함 */
	NEEDS_REVIEW,
	/** AI 채점을 하지 못함 (모델 미설정, 동의 없음, 한도 초과, 호출 실패) */
	PENDING;

	public String apiValue() {
		return ApiEnumValues.of(this);
	}

	public static Optional<GradingStatus> fromApiValue(String value) {
		return ApiEnumValues.parse(GradingStatus.class, value);
	}
}
