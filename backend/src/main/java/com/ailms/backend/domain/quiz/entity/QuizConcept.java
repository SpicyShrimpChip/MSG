package com.ailms.backend.domain.quiz.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 문제가 다루는 개념. conceptKey는 학습 분석의 concept_key와 같은 값이라 대시보드 개념과 이어 볼 수 있다
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuizConcept {

	@Column(name = "concept_key", nullable = false, length = 20)
	private String conceptKey;

	@Column(name = "concept_name", nullable = false, length = 20)
	private String conceptName;

	public static QuizConcept of(String conceptKey, String conceptName) {
		QuizConcept concept = new QuizConcept();
		concept.conceptKey = conceptKey;
		concept.conceptName = conceptName;
		return concept;
	}
}
