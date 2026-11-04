package com.ailms.backend.domain.quiz.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 퀴즈 만들기 요청 한 번 (quiz_generation). 같은 사용자가 같은 requestKey로 다시 보내면 새로 만들지 않는다.
 * 모델 호출 결과(제공사·토큰·시도 수)와 실패 이유 코드를 남겨 비용과 오류를 볼 수 있게 한다. 요청 글은 남기지 않는다.
 */
@Entity
@Table(name = "quiz_generation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuizGeneration {

	public static final int REQUEST_KEY_MAX_LENGTH = 64;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long userId;

	@Column(nullable = false)
	private Long courseId;

	@Column(nullable = false, length = REQUEST_KEY_MAX_LENGTH)
	private String requestKey;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private QuizMode mode;

	@Column(nullable = false, length = 60)
	private String questionTypes;

	@Column(nullable = false)
	private int questionCount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private QuizDifficulty difficulty;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private QuizGenerationStatus status;

	@Column(length = 30)
	private String errorCode;

	@Column(length = 10)
	private String provider;

	@Column(length = 100)
	private String model;

	private Long quizId;

	private Integer inputTokens;

	private Integer outputTokens;

	@Column(nullable = false)
	private int attempts;

	@Column(nullable = false)
	private LocalDateTime createdAt;

	private LocalDateTime finishedAt;

	// 이 시각이 지나면 끝난 것으로 본다 (V8). V7 행은 null
	private LocalDateTime deadlineAt;

	public static QuizGeneration start(Long userId, Long courseId, String requestKey, QuizMode mode,
			List<QuestionType> types, int questionCount, QuizDifficulty difficulty, LocalDateTime now,
			LocalDateTime deadlineAt) {
		QuizGeneration generation = new QuizGeneration();
		generation.userId = userId;
		generation.courseId = courseId;
		generation.requestKey = requestKey;
		generation.mode = mode;
		generation.questionTypes = types.stream().map(Enum::name).collect(Collectors.joining(","));
		generation.questionCount = questionCount;
		generation.difficulty = difficulty;
		generation.status = QuizGenerationStatus.RUNNING;
		generation.createdAt = now;
		generation.deadlineAt = deadlineAt;
		return generation;
	}

	public List<QuestionType> questionTypeList() {
		return Arrays.stream(questionTypes.split(",")).map(QuestionType::valueOf).toList();
	}

	public void succeed(Long quizId, String provider, String model, Integer inputTokens, Integer outputTokens,
			int attempts, LocalDateTime now) {
		this.status = QuizGenerationStatus.SUCCEEDED;
		this.quizId = quizId;
		record(provider, model, inputTokens, outputTokens, attempts);
		this.finishedAt = now;
	}

	public void fail(String errorCode, String provider, String model, Integer inputTokens, Integer outputTokens,
			int attempts, LocalDateTime now) {
		this.status = QuizGenerationStatus.FAILED;
		this.errorCode = errorCode;
		record(provider, model, inputTokens, outputTokens, attempts);
		this.finishedAt = now;
	}

	// 아직 진행 중이고 마감 전인지 (이 요청의 결과를 저장해도 되는지)
	public boolean isActiveAt(LocalDateTime now, java.time.Duration legacyTimeout) {
		if (status != QuizGenerationStatus.RUNNING) {
			return false;
		}
		return deadlineAt != null ? now.isBefore(deadlineAt) : now.isBefore(createdAt.plus(legacyTimeout));
	}

	// 마감이 지나 끝난 것으로 본다 (진행 중일 때만)
	public void timeOut(LocalDateTime now) {
		if (status == QuizGenerationStatus.RUNNING) {
			this.status = QuizGenerationStatus.TIMED_OUT;
			this.errorCode = "TIMED_OUT";
			this.finishedAt = now;
		}
	}

	private void record(String provider, String model, Integer inputTokens, Integer outputTokens, int attempts) {
		this.provider = provider;
		this.model = model;
		this.inputTokens = inputTokens;
		this.outputTokens = outputTokens;
		this.attempts = attempts;
	}
}
