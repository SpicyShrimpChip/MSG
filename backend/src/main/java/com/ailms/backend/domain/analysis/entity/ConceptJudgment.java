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

/**
 * 모델 하나가 개념 하나에 내린 판단. reportedStatus는 모델이 보고한 그대로이고, status는 검증 뒤 비교에 쓰는 값이다
 * (사용자가 쓴 메시지 근거가 없으면 판단 근거 부족으로 본다. 이유는 adjustedReason).
 * reason·needsCheck·recommendedActivity는 AI가 쓴 글이다.
 */
@Entity
@Table(name = "analysis_concept_judgment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConceptJudgment {

	public static final int REASON_MAX_LENGTH = 1000;
	public static final int NEEDS_CHECK_MAX_LENGTH = 500;
	public static final int ACTIVITY_MAX_LENGTH = 500;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long modelResultId;

	@Column(nullable = false, length = 20)
	private String conceptKey;

	@Column(nullable = false, length = 20)
	private String conceptName;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private UnderstandingStatus reportedStatus;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private UnderstandingStatus status;

	@Column(length = 200)
	private String adjustedReason;

	@Column(length = REASON_MAX_LENGTH)
	private String reason;

	@Column(length = NEEDS_CHECK_MAX_LENGTH)
	private String needsCheck;

	@Column(length = ACTIVITY_MAX_LENGTH)
	private String recommendedActivity;

	public static ConceptJudgment of(Long modelResultId, String conceptKey, String conceptName,
			UnderstandingStatus reportedStatus, UnderstandingStatus status, String adjustedReason, String reason,
			String needsCheck, String recommendedActivity) {
		ConceptJudgment judgment = new ConceptJudgment();
		judgment.modelResultId = modelResultId;
		judgment.conceptKey = conceptKey;
		judgment.conceptName = conceptName;
		judgment.reportedStatus = reportedStatus;
		judgment.status = status;
		judgment.adjustedReason = adjustedReason;
		judgment.reason = reason;
		judgment.needsCheck = needsCheck;
		judgment.recommendedActivity = recommendedActivity;
		return judgment;
	}
}
