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

// 판단 근거가 된 메시지 (검증을 통과한 것만). note는 AI가 쓴 짧은 설명이다
@Entity
@Table(name = "analysis_judgment_evidence")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JudgmentEvidence {

	public static final int NOTE_MAX_LENGTH = 300;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long judgmentId;

	@Column(nullable = false)
	private Long messageId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private EvidenceKind kind;

	@Column(length = NOTE_MAX_LENGTH)
	private String note;

	public static JudgmentEvidence of(Long judgmentId, Long messageId, EvidenceKind kind, String note) {
		JudgmentEvidence evidence = new JudgmentEvidence();
		evidence.judgmentId = judgmentId;
		evidence.messageId = messageId;
		evidence.kind = kind;
		evidence.note = note;
		return evidence;
	}
}
