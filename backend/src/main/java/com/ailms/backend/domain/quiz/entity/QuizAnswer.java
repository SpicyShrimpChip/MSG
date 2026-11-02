package com.ailms.backend.domain.quiz.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 제출 한 번의 문제별 채점 결과. 답하지 않은 문제는 choiceIndex -1(틀림)이다
@Entity
@Table(name = "quiz_answer")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuizAnswer {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long submissionId;

	@Column(nullable = false)
	private Long questionId;

	@Column(nullable = false)
	private int choiceIndex;

	@Column(nullable = false)
	private boolean correct;

	public static QuizAnswer of(Long submissionId, Long questionId, int choiceIndex, boolean correct) {
		QuizAnswer answer = new QuizAnswer();
		answer.submissionId = submissionId;
		answer.questionId = questionId;
		answer.choiceIndex = choiceIndex;
		answer.correct = correct;
		return answer;
	}
}
