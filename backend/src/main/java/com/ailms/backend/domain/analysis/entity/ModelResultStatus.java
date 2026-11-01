package com.ailms.backend.domain.analysis.entity;

public enum ModelResultStatus {

	/** 응답을 받았고 형식 검증을 통과함 */
	SUCCEEDED,
	/** 호출 실패 (시간 초과, 한도 초과, 인증 실패 등) */
	FAILED,
	/** 응답은 왔지만 약속한 JSON 형식이 아님 */
	INVALID_OUTPUT,
	/** API 키나 모델 이름이 없어 부르지 않음 (연결된 척하지 않는다) */
	NOT_CONFIGURED;

	public String apiValue() {
		return ApiValues.of(this);
	}
}
