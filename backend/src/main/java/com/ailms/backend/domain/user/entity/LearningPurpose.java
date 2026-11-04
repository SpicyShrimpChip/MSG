package com.ailms.backend.domain.user.entity;

import com.ailms.backend.global.common.ApiEnumValues;
import java.util.Optional;

/**
 * 사용자의 주된 학습 목적 (가입·마이페이지에서 고른다). 퀴즈를 만들 때 기본 모드로 쓰고, 퀴즈마다 다른 모드를 골라도 된다.
 */
public enum LearningPurpose {
	/** 시험 대비 */
	EXAM_PREP,
	/** 자기개발 */
	SELF_DEVELOPMENT,
	/** 업무 활용 */
	WORK,
	/** 아직 정하지 않음 (기존 회원 기본값) */
	UNDECIDED;

	public String apiValue() {
		return ApiEnumValues.of(this);
	}

	public static Optional<LearningPurpose> fromApiValue(String value) {
		return ApiEnumValues.parse(LearningPurpose.class, value);
	}
}
