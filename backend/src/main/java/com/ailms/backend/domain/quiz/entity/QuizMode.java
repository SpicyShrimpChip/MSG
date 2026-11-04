package com.ailms.backend.domain.quiz.entity;

import com.ailms.backend.global.common.ApiEnumValues;
import java.util.Optional;

/**
 * 퀴즈 학습 모드. 같은 과목·개념이라도 모드마다 문제의 초점과 결과 판단이 다르다.
 * V6에서 만든 퀴즈는 모드가 없다(null).
 */
public enum QuizMode {
	/** 시험 대비: 출제 가능성이 높다고 추정되는 핵심 개념 중심 (출제를 보장하지 않는다) */
	EXAM_PREP,
	/** 자기개발: 이해 검증과 다음 단계로 넘어갈 준비 확인 */
	SELF_DEVELOPMENT,
	/** 업무 활용: 직무 상황에서 지식을 쓰는 방법 */
	WORK;

	public String apiValue() {
		return ApiEnumValues.of(this);
	}

	public static Optional<QuizMode> fromApiValue(String value) {
		return ApiEnumValues.parse(QuizMode.class, value);
	}
}
