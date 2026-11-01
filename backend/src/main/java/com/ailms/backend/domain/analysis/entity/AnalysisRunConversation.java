package com.ailms.backend.domain.analysis.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 분석에 쓴 대화. 대화를 지우면 그 대화를 쓴 분석도 지운다 (AnalysisCleanup)
@Entity
@Table(name = "analysis_run_conversation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnalysisRunConversation {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long runId;

	@Column(nullable = false)
	private Long conversationId;

	public static AnalysisRunConversation of(Long runId, Long conversationId) {
		AnalysisRunConversation row = new AnalysisRunConversation();
		row.runId = runId;
		row.conversationId = conversationId;
		return row;
	}
}
