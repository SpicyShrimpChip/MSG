package com.ailms.backend.domain.analysis.entity;

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
 * 개념 하나의 숙련도 기록 한 줄 (변화 추이용). 규칙으로 계산한 값을 그대로 남기고 고치지 않는다.
 * 분석(analysis_run)을 지우면 함께 지워진다.
 */
@Entity
@Table(name = "concept_mastery")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConceptMastery {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long userId;

	@Column(nullable = false)
	private Long courseId;

	@Column(nullable = false)
	private Long analysisRunId;

	@Column(nullable = false, length = 20)
	private String conceptKey;

	@Column(nullable = false, length = 20)
	private String conceptName;

	@Column(nullable = false, length = 30)
	private String level;

	private Double score;

	@Column(nullable = false)
	private double evidenceWeight;

	@Column(nullable = false)
	private int quizGradedCount;

	private Double quizRate;

	@Column(nullable = false)
	private int conversationCount;

	@Column(length = 20)
	private String conversationStatus;

	@Column(nullable = false, length = 20)
	private String formulaVersion;

	@Column(nullable = false)
	private LocalDateTime recordedAt;

	public static ConceptMastery create(Long userId, Long courseId, Long analysisRunId, String conceptKey,
			String conceptName, String level, Double score, double evidenceWeight, int quizGradedCount,
			Double quizRate, int conversationCount, String conversationStatus, String formulaVersion,
			LocalDateTime recordedAt) {
		ConceptMastery row = new ConceptMastery();
		row.userId = userId;
		row.courseId = courseId;
		row.analysisRunId = analysisRunId;
		row.conceptKey = conceptKey;
		row.conceptName = conceptName;
		row.level = level;
		row.score = score;
		row.evidenceWeight = evidenceWeight;
		row.quizGradedCount = quizGradedCount;
		row.quizRate = quizRate;
		row.conversationCount = conversationCount;
		row.conversationStatus = conversationStatus;
		row.formulaVersion = formulaVersion;
		row.recordedAt = recordedAt;
		return row;
	}
}
