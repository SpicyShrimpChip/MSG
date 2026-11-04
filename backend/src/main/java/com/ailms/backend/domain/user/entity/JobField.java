package com.ailms.backend.domain.user.entity;

import com.ailms.backend.global.common.ApiEnumValues;
import java.util.Optional;

/**
 * 관심 직무 분야 (업무 활용 모드에서 상황 문제를 만들 때 쓴다). 하는 일을 자세히 적게 하지 않고 분야만 고르게 한다.
 * 분야를 늘릴 때는 여기에 값을 추가하고 프론트 constants/learning.ts의 목록에도 같은 값을 넣는다 (DB는 문자열이라 바꿀 필요 없음).
 * 목록에 없는 분야는 OTHER + 짧은 이름(job_field_other)으로 받는다.
 */
public enum JobField {

	IT_CONSULTING("IT 컨설팅"),
	NETWORK_SECURITY("네트워크·보안"),
	SOFTWARE_DEVELOPMENT("소프트웨어 개발"),
	DATA_ANALYSIS("데이터 분석"),
	SYSTEM_CLOUD_OPERATIONS("시스템·클라우드 운영"),
	OTHER("기타");

	public static final int OTHER_MAX_LENGTH = 30;

	private final String label;

	JobField(String label) {
		this.label = label;
	}

	// 모델에 보내는 분야 이름. 기타면 사용자가 쓴 이름을 쓴다
	public String label() {
		return label;
	}

	public String apiValue() {
		return ApiEnumValues.of(this);
	}

	public static Optional<JobField> fromApiValue(String value) {
		return ApiEnumValues.parse(JobField.class, value);
	}
}
