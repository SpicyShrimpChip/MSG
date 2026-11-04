package com.ailms.backend.domain.quiz.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// AI가 제안한 다음 학습 개념 하나 (quiz_suggestion). 실제 학습 결과가 아니다
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuizSuggestion {

	public static final int CONCEPT_MAX_LENGTH = 40;
	public static final int REASON_MAX_LENGTH = 300;

	@Enumerated(EnumType.STRING)
	@Column(name = "kind", nullable = false, length = 20)
	private SuggestionKind kind;

	@Column(name = "concept", nullable = false, length = CONCEPT_MAX_LENGTH)
	private String concept;

	@Column(name = "reason", length = REASON_MAX_LENGTH)
	private String reason;

	public static QuizSuggestion of(SuggestionKind kind, String concept, String reason) {
		QuizSuggestion suggestion = new QuizSuggestion();
		suggestion.kind = kind;
		suggestion.concept = concept;
		suggestion.reason = reason;
		return suggestion;
	}
}
