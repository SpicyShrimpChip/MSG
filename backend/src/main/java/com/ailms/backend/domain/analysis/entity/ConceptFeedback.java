package com.ailms.backend.domain.analysis.entity;

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
 * 학습 모드에서 사용자가 개념마다 남긴 의견. 과목·개념마다 하나이고, 수정하면 덮어쓰고 취소하면 지운다.
 * AI 분석 결과와 따로 보관하며 숙련도·AI 판단을 바꾸지 않는다.
 */
@Entity
@Table(name = "concept_feedback")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConceptFeedback {

	public static final int COMMENT_MAX_LENGTH = 200;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long userId;

	@Column(nullable = false)
	private Long courseId;

	@Column(nullable = false, length = 20)
	private String conceptKey;

	@Column(nullable = false, length = 20)
	private String conceptName;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private FeedbackType feedback;

	@Column(length = COMMENT_MAX_LENGTH)
	private String comment;

	private Long analysisRunId;

	@Column(nullable = false)
	private LocalDateTime createdAt;

	@Column(nullable = false)
	private LocalDateTime updatedAt;

	public static ConceptFeedback create(Long userId, Long courseId, String conceptKey, String conceptName,
			FeedbackType feedback, String comment, Long analysisRunId, LocalDateTime now) {
		ConceptFeedback row = new ConceptFeedback();
		row.userId = userId;
		row.courseId = courseId;
		row.conceptKey = conceptKey;
		row.conceptName = conceptName;
		row.feedback = feedback;
		row.comment = comment;
		row.analysisRunId = analysisRunId;
		row.createdAt = now;
		row.updatedAt = now;
		return row;
	}

	public void change(FeedbackType feedback, String comment, Long analysisRunId, LocalDateTime now) {
		this.feedback = feedback;
		this.comment = comment;
		this.analysisRunId = analysisRunId;
		this.updatedAt = now;
	}
}
