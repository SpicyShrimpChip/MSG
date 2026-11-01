package com.ailms.backend.domain.analysis.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 개념 하나에 대한 모델 비교 결과 (ConsensusCalculator가 계산). 정확도나 숙련도 점수가 아니다
@Entity
@Table(name = "analysis_concept_consensus")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConceptConsensus {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long runId;

	@Column(nullable = false, length = 20)
	private String conceptKey;

	@Column(nullable = false, length = 20)
	private String conceptName;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ConsensusOutcome outcome;

	// 일치한 상태. 의견 불일치·결과 없음이면 null
	@Enumerated(EnumType.STRING)
	@Column(length = 30)
	private UnderstandingStatus status;

	@Column(nullable = false)
	private int judgedCount;

	@Column(nullable = false)
	private int agreeCount;

	@Column(nullable = false)
	private int calledCount;

	public static ConceptConsensus of(Long runId, String conceptKey, String conceptName, ConsensusOutcome outcome,
			UnderstandingStatus status, int judgedCount, int agreeCount, int calledCount) {
		ConceptConsensus consensus = new ConceptConsensus();
		consensus.runId = runId;
		consensus.conceptKey = conceptKey;
		consensus.conceptName = conceptName;
		consensus.outcome = outcome;
		consensus.status = status;
		consensus.judgedCount = judgedCount;
		consensus.agreeCount = agreeCount;
		consensus.calledCount = calledCount;
		return consensus;
	}
}
