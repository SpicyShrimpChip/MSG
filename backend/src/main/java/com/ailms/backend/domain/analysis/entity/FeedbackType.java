package com.ailms.backend.domain.analysis.entity;

import java.util.Optional;

/**
 * 학습 모드에서 사용자가 개념마다 남기는 의견. AI 결과·숙련도를 직접 바꾸지 않는다.
 */
public enum FeedbackType {

	/** 이미 알아요: 확인 퀴즈로 검증을 권한다 */
	ALREADY_KNOW,
	/** 더 공부할래요: 학습 추천 순서를 앞당긴다 */
	WANT_TO_STUDY;

	public String apiValue() {
		return ApiValues.of(this);
	}

	public static Optional<FeedbackType> fromApiValue(String value) {
		return ApiValues.parse(FeedbackType.class, value);
	}
}
