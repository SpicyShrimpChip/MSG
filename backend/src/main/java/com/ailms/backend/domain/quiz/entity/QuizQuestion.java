package com.ailms.backend.domain.quiz.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 객관식 문제. correctChoiceIndex는 보기 순서(0부터)이고 제출 뒤 결과에만 내보낸다.
 * aiExplanation은 AI가 쓴 해설이다. 정오는 AI가 아니라 QuizGrader가 이 정답 번호로 정한다.
 */
@Entity
@Table(name = "quiz_question")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuizQuestion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long quizId;

	@Column(nullable = false)
	private int sortOrder;

	@Column(nullable = false, length = 1000)
	private String content;

	@ElementCollection
	@CollectionTable(name = "quiz_choice", joinColumns = @JoinColumn(name = "question_id"))
	@OrderColumn(name = "sort_order")
	@Column(name = "content", nullable = false, length = 500)
	private List<String> choices = new ArrayList<>();

	@Column(nullable = false)
	private int correctChoiceIndex;

	@Column(length = 2000)
	private String aiExplanation;

	@ElementCollection
	@CollectionTable(name = "quiz_question_concept", joinColumns = @JoinColumn(name = "question_id"))
	@OrderColumn(name = "sort_order")
	private List<QuizConcept> concepts = new ArrayList<>();

	public static QuizQuestion create(Long quizId, int sortOrder, String content, List<String> choices,
			int correctChoiceIndex, String aiExplanation, List<QuizConcept> concepts) {
		if (correctChoiceIndex < 0 || correctChoiceIndex >= choices.size()) {
			throw new IllegalArgumentException("정답 번호가 보기 범위를 벗어났어요.");
		}
		QuizQuestion question = new QuizQuestion();
		question.quizId = quizId;
		question.sortOrder = sortOrder;
		question.content = content;
		question.choices.addAll(choices);
		question.correctChoiceIndex = correctChoiceIndex;
		question.aiExplanation = aiExplanation;
		question.concepts.addAll(concepts);
		return question;
	}
}
