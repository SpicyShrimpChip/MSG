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
 * 분석 한 번: 과목 하나의 분석 대상 대화를 모든 모델에 한 번씩 보낸 것. 모델별 결과·개념 비교·근거는 이 행에 딸려 있다.
 */
@Entity
@Table(name = "analysis_run")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnalysisRun {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long userId;

	@Column(nullable = false)
	private Long courseId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private AnalysisRunStatus status;

	@Column(nullable = false, length = 40)
	private String promptVersion;

	@Column(nullable = false)
	private int conversationCount;

	@Column(nullable = false)
	private int messageCount;

	@Column(nullable = false)
	private int truncatedMessageCount;

	@Column(nullable = false)
	private int inputChars;

	@Column(nullable = false)
	private LocalDateTime createdAt;

	private LocalDateTime completedAt;

	public static AnalysisRun start(Long userId, Long courseId, String promptVersion, int conversationCount,
			int messageCount, int truncatedMessageCount, int inputChars, LocalDateTime now) {
		AnalysisRun run = new AnalysisRun();
		run.userId = userId;
		run.courseId = courseId;
		run.status = AnalysisRunStatus.RUNNING;
		run.promptVersion = promptVersion;
		run.conversationCount = conversationCount;
		run.messageCount = messageCount;
		run.truncatedMessageCount = truncatedMessageCount;
		run.inputChars = inputChars;
		run.createdAt = now;
		return run;
	}

	public void finish(AnalysisRunStatus status, LocalDateTime now) {
		this.status = status;
		this.completedAt = now;
	}
}
