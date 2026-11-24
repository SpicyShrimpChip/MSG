package com.msg.backend.domain.quiz.entity;

import com.msg.backend.global.common.ApiEnumValues;
import java.util.Optional;

/**
 * 개정 블룸 교육목표 분류(Anderson &amp; Krathwohl, 2001)의 인지 과정 6수준. 문제가 어떤 사고를 평가하려는지(출제 의도)를 나타낸다.
 * 난이도(QuizDifficulty)와 별개다: 쉬운 적용 문제도, 어려운 기억 문제도 있을 수 있다.
 * 문제의 수준은 AI가 지정한 값이고, 서버는 형식(요청한 수준 안인지, 창조는 서술형인지)만 확인한다.
 * 실제로 그 수준의 사고를 요구하는지는 코드로 판정할 수 없어 화면에 "AI가 지정한 수준"으로 보여 준다.
 */
public enum BloomLevel {
	/** 기억: 사실·용어·정의를 떠올린다 */
	REMEMBER("기억"),
	/** 이해: 자기 말로 설명하고 비교·분류한다 */
	UNDERSTAND("이해"),
	/** 적용: 주어진 상황에 절차·개념을 쓴다 */
	APPLY("적용"),
	/** 분석: 구성 요소와 관계, 원인을 나눠 본다 */
	ANALYZE("분석"),
	/** 평가: 기준을 세워 대안을 판단하고 근거를 댄다 */
	EVALUATE("평가"),
	/** 창조: 요구사항을 만족하는 새 구성·설계를 만든다 (서술형만) */
	CREATE("창조");

	private final String label;

	BloomLevel(String label) {
		this.label = label;
	}

	public String label() {
		return label;
	}

	public String apiValue() {
		return ApiEnumValues.of(this);
	}

	/** API 값("understand")을 읽는다. 모델이 한글 이름("이해")으로 적어도 같은 수준으로 본다 */
	public static Optional<BloomLevel> fromApiValue(String value) {
		Optional<BloomLevel> parsed = ApiEnumValues.parse(BloomLevel.class, value);
		if (parsed.isPresent() || value == null) {
			return parsed;
		}
		String trimmed = value.strip();
		for (BloomLevel level : values()) {
			if (level.label.equals(trimmed)) {
				return Optional.of(level);
			}
		}
		return Optional.empty();
	}
}
