package com.ailms.backend.domain.quiz.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 퀴즈 제출 한 번. 다시 풀면 새 행이 쌓이고, 화면은 가장 최근 것(id가 가장 큰 것)을 쓴다
@Entity
@Table(name = "quiz_submission")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuizSubmission {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long quizId;

	@Column(nullable = false)
	private Long userId;

	@Column(nullable = false)
	private int correctCount;

	@Column(nullable = false)
	private int totalCount;

	@Column(nullable = false)
	private LocalDateTime submittedAt;

	public static QuizSubmission of(Long quizId, Long userId, int correctCount, int totalCount, LocalDateTime now) {
		QuizSubmission submission = new QuizSubmission();
		submission.quizId = quizId;
		submission.userId = userId;
		submission.correctCount = correctCount;
		submission.totalCount = totalCount;
		submission.submittedAt = now;
		return submission;
	}
}
