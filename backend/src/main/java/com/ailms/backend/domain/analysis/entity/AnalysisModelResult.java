package com.ailms.backend.domain.analysis.entity;

import com.ailms.backend.global.llm.LlmProvider;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 모델 하나의 결과. 모델이 돌려준 글은 검증 전 원본 그대로(rawOutput) 보관하고,
 * 검증을 통과한 개념별 판단은 ConceptJudgment에 따로 둔다.
 */
@Entity
@Table(name = "analysis_model_result")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnalysisModelResult {

	public static final int RAW_OUTPUT_MAX_LENGTH = 60_000;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long runId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private LlmProvider provider;

	@Column(length = 100)
	private String model;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ModelResultStatus status;

	@Column(length = 40)
	private String errorCode;

	@Column(length = RAW_OUTPUT_MAX_LENGTH)
	private String rawOutput;

	private Integer inputTokens;

	private Integer outputTokens;

	@Column(nullable = false)
	private int attemptCount;

	private Long latencyMs;

	@Column(nullable = false)
	private int droppedEvidenceCount;

	@Column(nullable = false)
	private LocalDateTime createdAt;

	public static AnalysisModelResult of(Long runId, LlmProvider provider, String model, ModelResultStatus status,
			String errorCode, String rawOutput, Integer inputTokens, Integer outputTokens, int attemptCount,
			Long latencyMs, int droppedEvidenceCount, LocalDateTime now) {
		AnalysisModelResult result = new AnalysisModelResult();
		result.runId = runId;
		result.provider = provider;
		result.model = model;
		result.status = status;
		result.errorCode = errorCode;
		result.rawOutput = rawOutput == null || rawOutput.length() <= RAW_OUTPUT_MAX_LENGTH
				? rawOutput : rawOutput.substring(0, RAW_OUTPUT_MAX_LENGTH);
		result.inputTokens = inputTokens;
		result.outputTokens = outputTokens;
		result.attemptCount = attemptCount;
		result.latencyMs = latencyMs;
		result.droppedEvidenceCount = droppedEvidenceCount;
		result.createdAt = now;
		return result;
	}
}
