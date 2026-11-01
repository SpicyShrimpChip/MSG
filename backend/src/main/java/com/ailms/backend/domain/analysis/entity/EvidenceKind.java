package com.ailms.backend.domain.analysis.entity;

import java.util.Optional;

/**
 * 근거 메시지의 종류. 사용자가 직접 쓴 글과 AI가 준 설명을 나눈다.
 * AI가 설명했다는 사실만으로는 사용자가 이해했다고 보지 않는다.
 */
public enum EvidenceKind {

	/** 사용자가 개념을 직접 설명함 */
	USER_EXPLANATION(true),
	/** 사용자가 문제를 직접 풀거나 적용함 */
	USER_SOLUTION(true),
	/** 사용자의 질문 (모르는 부분이 드러남) */
	USER_QUESTION(true),
	/** AI가 사용자에게 준 설명 */
	AI_EXPLANATION(false);

	private final boolean writtenByUser;

	EvidenceKind(boolean writtenByUser) {
		this.writtenByUser = writtenByUser;
	}

	public boolean writtenByUser() {
		return writtenByUser;
	}

	public String apiValue() {
		return ApiValues.of(this);
	}

	public static Optional<EvidenceKind> fromApiValue(String value) {
		return ApiValues.parse(EvidenceKind.class, value);
	}
}
