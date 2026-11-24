package com.msg.backend.domain.material.entity;

import com.msg.backend.domain.quiz.entity.QuizMode;
import com.msg.backend.global.common.Texts;
import com.msg.backend.global.common.VerificationStatus;
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
 * 학습자료 요약본 (material_summary, V5). 자료 하나·학습 목적 하나에 한 행이고, 다시 만들면 같은 행을 바꾼다.
 * summaryJson은 AI가 쓴 요약을 서버가 검증해 다시 쓴 JSON이다(인용문·위치·검증 상태는 서버가 원문에서 확인한 값).
 * contentHash는 요약을 만든 원본 파일의 SHA-256이라 파일이 바뀌었는지 알 수 있다.
 */
@Entity
@Table(name = "material_summary")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MaterialSummary {

	public static final int JSON_MAX_LENGTH = 30000;
	public static final int SCOPE_MAX_LENGTH = 300;

	public enum Status {
		RUNNING, SUCCEEDED, FAILED
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long userId;

	@Column(nullable = false)
	private Long materialId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private QuizMode purpose;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Status status;

	private LocalDateTime deadlineAt;

	@Column(length = 64)
	private String contentHash;

	@Column(length = 40)
	private String promptVersion;

	@Column(length = 10)
	private String provider;

	@Column(length = 100)
	private String model;

	@Column(length = JSON_MAX_LENGTH)
	private String summaryJson;

	@Enumerated(EnumType.STRING)
	@Column(length = 20)
	private VerificationStatus verificationStatus;

	@Column(length = SCOPE_MAX_LENGTH)
	private String sourceScope;

	@Column(nullable = false)
	private boolean sourceTruncated;

	private LocalDateTime createdAt;

	@Column(nullable = false)
	private LocalDateTime updatedAt;

	public static MaterialSummary running(Long userId, Long materialId, QuizMode purpose, LocalDateTime deadline,
			LocalDateTime now) {
		MaterialSummary summary = new MaterialSummary();
		summary.userId = userId;
		summary.materialId = materialId;
		summary.purpose = purpose;
		summary.status = Status.RUNNING;
		summary.deadlineAt = deadline;
		summary.updatedAt = now;
		return summary;
	}

	/** 다시 만들기 시작 (이전 결과는 성공할 때까지 그대로 둔다) */
	public void restart(LocalDateTime deadline, LocalDateTime now) {
		this.status = Status.RUNNING;
		this.deadlineAt = deadline;
		this.updatedAt = now;
	}

	public boolean isRunningAt(LocalDateTime now) {
		return status == Status.RUNNING && deadlineAt != null && now.isBefore(deadlineAt);
	}

	public boolean hasResult() {
		return summaryJson != null;
	}

	public void succeed(String contentHash, String promptVersion, String provider, String model, String summaryJson,
			VerificationStatus verificationStatus, String sourceScope, boolean sourceTruncated, LocalDateTime now) {
		this.status = Status.SUCCEEDED;
		this.deadlineAt = null;
		this.contentHash = contentHash;
		this.promptVersion = promptVersion;
		this.provider = provider;
		this.model = model;
		this.summaryJson = summaryJson;
		this.verificationStatus = verificationStatus;
		this.sourceScope = Texts.clip(sourceScope, SCOPE_MAX_LENGTH);
		this.sourceTruncated = sourceTruncated;
		this.createdAt = now;
		this.updatedAt = now;
	}

	/** 실패·시간 초과: 이전 결과가 있으면 그 결과로 되돌린다 */
	public void finishFailed(LocalDateTime now) {
		this.status = hasResult() ? Status.SUCCEEDED : Status.FAILED;
		this.deadlineAt = null;
		this.updatedAt = now;
	}
}
