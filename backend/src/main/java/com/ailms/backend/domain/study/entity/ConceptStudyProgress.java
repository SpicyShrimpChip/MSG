package com.ailms.backend.domain.study.entity;

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
 * 개념별 학습 기록 (concept_study_progress, V12). 사용자·자료·개념 키마다 한 행이다.
 * "학습 완료"는 공부했다는 기록일 뿐이고, 이해 여부·숙련도는 바꾸지 않는다(확인 퀴즈 채점 결과로만 판단).
 * 다시 공부해도 행을 지우거나 새로 만들지 않고, 처음 완료 시각은 두고 최근 학습 시각과 횟수만 바꾼다.
 */
@Entity
@Table(name = "concept_study_progress")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConceptStudyProgress {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long userId;

	@Column(nullable = false)
	private Long courseId;

	@Column(nullable = false)
	private Long materialId;

	@Column(nullable = false, length = 20)
	private String conceptKey;

	@Column(nullable = false, length = 20)
	private String conceptName;

	@Column(nullable = false)
	private LocalDateTime firstCompletedAt;

	@Column(nullable = false)
	private LocalDateTime lastStudiedAt;

	@Column(nullable = false)
	private int studyCount;

	@Column(nullable = false)
	private LocalDateTime createdAt;

	@Column(nullable = false)
	private LocalDateTime updatedAt;

	public static ConceptStudyProgress complete(Long userId, Long courseId, Long materialId, String conceptKey,
			String conceptName, LocalDateTime now) {
		ConceptStudyProgress progress = new ConceptStudyProgress();
		progress.userId = userId;
		progress.courseId = courseId;
		progress.materialId = materialId;
		progress.conceptKey = conceptKey;
		progress.conceptName = conceptName;
		progress.firstCompletedAt = now;
		progress.lastStudiedAt = now;
		progress.studyCount = 1;
		progress.createdAt = now;
		progress.updatedAt = now;
		return progress;
	}

	// 복습이 필요한 개념을 다시 공부하고 완료했다 (처음 완료 시각은 그대로)
	public void restudy(LocalDateTime now) {
		this.lastStudiedAt = now;
		this.studyCount++;
		this.updatedAt = now;
	}
}
