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
 * 모델 하나를 한 번 부른 기록 (quiz_ai_call). 재시도는 attempts로 센다. 실패한 호출도 비용이 들 수 있어 남긴다.
 * 요청·응답 글은 남기지 않는다.
 */
@Entity
@Table(name = "quiz_ai_call")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuizAiCall {

	public static final String GENERATION = "GENERATION";
	public static final String GRADING = "GRADING";
	public static final String CONCEPTS = "CONCEPTS";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long userId;

	@Column(nullable = false, length = 20)
	private String purpose;

	private Long generationId;

	private Long gradingRunId;

	@Column(nullable = false, length = 10)
	private String provider;

	@Column(length = 100)
	private String model;

	@Column(nullable = false)
	private int attempts;

	// SUCCEEDED / FAILED / INVALID_OUTPUT / TIMED_OUT
	@Column(nullable = false, length = 20)
	private String outcome;

	@Column(length = 30)
	private String errorCode;

	private Integer inputTokens;

	private Integer outputTokens;

	@Column(nullable = false)
	private long latencyMs;

	@Column(nullable = false)
	private LocalDateTime createdAt;

	public static QuizAiCall of(Long userId, String purpose, Long generationId, Long gradingRunId, String provider,
			String model, int attempts, String outcome, String errorCode, Integer inputTokens, Integer outputTokens,
			long latencyMs, LocalDateTime now) {
		QuizAiCall call = new QuizAiCall();
		call.userId = userId;
		call.purpose = purpose;
		call.generationId = generationId;
		call.gradingRunId = gradingRunId;
		call.provider = provider;
		call.model = model;
		call.attempts = attempts;
		call.outcome = outcome;
		call.errorCode = errorCode;
		call.inputTokens = inputTokens;
		call.outputTokens = outputTokens;
		call.latencyMs = latencyMs;
		call.createdAt = now;
		return call;
	}
}
