package com.ailms.backend.domain.quiz.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 서술형 채점 기준 하나와 배점 (quiz_question_rubric). 배점 합이 문제의 만점이다
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuizRubricItem {

	public static final int CRITERION_MAX_LENGTH = 200;
	// 부분 충족이 1점 이상이 되도록 2점부터 받는다
	public static final int MIN_POINTS = 2;
	public static final int MAX_POINTS = 5;

	@Column(name = "criterion", nullable = false, length = CRITERION_MAX_LENGTH)
	private String criterion;

	@Column(name = "points", nullable = false)
	private int points;

	public static QuizRubricItem of(String criterion, int points) {
		QuizRubricItem item = new QuizRubricItem();
		item.criterion = criterion;
		item.points = points;
		return item;
	}
}
