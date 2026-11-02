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

// 객관식 퀴즈 하나. 문제는 QuizQuestion에 따로 두고, 올린 본인(userId)만 볼 수 있다
@Entity
@Table(name = "quiz")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Quiz {

	public static final int TITLE_MAX_LENGTH = 100;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long userId;

	@Column(nullable = false)
	private Long courseId;

	// 이 퀴즈를 만든 자료. 자료 없이 만든 퀴즈는 null
	private Long materialId;

	@Column(nullable = false, length = TITLE_MAX_LENGTH)
	private String title;

	@Column(nullable = false)
	private LocalDateTime createdAt;

	public static Quiz create(Long userId, Long courseId, Long materialId, String title, LocalDateTime now) {
		Quiz quiz = new Quiz();
		quiz.userId = userId;
		quiz.courseId = courseId;
		quiz.materialId = materialId;
		quiz.title = title;
		quiz.createdAt = now;
		return quiz;
	}
}
