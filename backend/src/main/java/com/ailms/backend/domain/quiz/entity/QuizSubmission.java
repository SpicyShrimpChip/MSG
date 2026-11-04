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

/**
 * 퀴즈 제출 한 번. 다시 풀면 새 행이 쌓이고, 화면은 가장 최근 것(id가 가장 큰 것)을 쓴다.
 * score·maxScore는 채점이 끝난(GRADED) 답만 서버 코드가 더한 값이다. AI 채점을 기다리거나 확인이 필요한 답은
 * pendingCount·reviewCount로 따로 센다. correctCount는 V6부터 쓰던 "완전히 맞힌 문제 수"다.
 */
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
	private int score;

	@Column(nullable = false)
	private int maxScore;

	@Column(nullable = false)
	private int pendingCount;

	@Column(nullable = false)
	private int reviewCount;

	@Column(nullable = false)
	private LocalDateTime submittedAt;

	// 객관식만 있는 퀴즈 (V6와 같은 결과)
	public static QuizSubmission of(Long quizId, Long userId, int correctCount, int totalCount, LocalDateTime now) {
		return of(quizId, userId, correctCount, totalCount, correctCount, totalCount, 0, 0, now);
	}

	public static QuizSubmission of(Long quizId, Long userId, int correctCount, int totalCount, int score,
			int maxScore, int pendingCount, int reviewCount, LocalDateTime now) {
		QuizSubmission submission = new QuizSubmission();
		submission.quizId = quizId;
		submission.userId = userId;
		submission.correctCount = correctCount;
		submission.totalCount = totalCount;
		submission.score = score;
		submission.maxScore = maxScore;
		submission.pendingCount = pendingCount;
		submission.reviewCount = reviewCount;
		submission.submittedAt = now;
		return submission;
	}

	// 채점 대기였던 답을 다시 채점한 뒤 합계를 바꾼다
	public void updateTotals(int correctCount, int score, int maxScore, int pendingCount, int reviewCount) {
		this.correctCount = correctCount;
		this.score = score;
		this.maxScore = maxScore;
		this.pendingCount = pendingCount;
		this.reviewCount = reviewCount;
	}
}
