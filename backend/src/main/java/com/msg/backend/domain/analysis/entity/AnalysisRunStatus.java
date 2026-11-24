package com.msg.backend.domain.analysis.entity;

import com.msg.backend.global.common.ApiEnumValues;

public enum AnalysisRunStatus {

	/** 모델에 보내는 중 */
	RUNNING,
	/** 부른 모델이 모두 성공 */
	COMPLETED,
	/** 일부 모델만 성공 */
	PARTIAL,
	/** 성공한 모델이 없음 */
	FAILED;

	public String apiValue() {
		return ApiEnumValues.of(this);
	}
}
