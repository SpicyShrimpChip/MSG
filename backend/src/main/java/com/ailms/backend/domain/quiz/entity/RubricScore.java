package com.ailms.backend.domain.quiz.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 서술형 채점 기준 하나의 결과 (quiz_answer_rubric_score, 순서는 문제의 rubric과 같다).
 * level은 모델 판단의 다수 의견(코드)이고, points는 그 수준으로 코드가 계산한 점수다. reason만 AI 글이다.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RubricScore {

	public static final int REASON_MAX_LENGTH = 500;

	// 다수 의견이 없으면 null
	@Enumerated(EnumType.STRING)
	@Column(name = "level", length = 10)
	private RubricLevel level;

	@Column(name = "points", nullable = false)
	private int points;

	@Column(name = "agree_count", nullable = false)
	private int agreeCount;

	@Column(name = "judged_count", nullable = false)
	private int judgedCount;

	@Column(name = "reason", length = REASON_MAX_LENGTH)
	private String reason;

	public static RubricScore of(RubricLevel level, int points, int agreeCount, int judgedCount, String reason) {
		RubricScore score = new RubricScore();
		score.level = level;
		score.points = points;
		score.agreeCount = agreeCount;
		score.judgedCount = judgedCount;
		score.reason = reason;
		return score;
	}
}
