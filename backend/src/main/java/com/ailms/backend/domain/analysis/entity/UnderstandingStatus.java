package com.ailms.backend.domain.analysis.entity;

import java.util.Optional;

/**
 * 개념 하나에 대한 사용자 이해 상태. 점수가 아니다.
 * 근거가 부족한 것(INSUFFICIENT_EVIDENCE)은 이해 부족(NOT_UNDERSTOOD)과 다르며 서로 바꾸지 않는다.
 */
public enum UnderstandingStatus {

	/** 이해 */
	UNDERSTOOD,
	/** 부분 이해 */
	PARTIAL,
	/** 이해 부족 */
	NOT_UNDERSTOOD,
	/** 판단 근거 부족 */
	INSUFFICIENT_EVIDENCE;

	public String apiValue() {
		return ApiValues.of(this);
	}

	public static Optional<UnderstandingStatus> fromApiValue(String value) {
		return ApiValues.parse(UnderstandingStatus.class, value);
	}
}
