package com.ailms.backend.global.storage;

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
 * 지워야 할 저장소 파일 한 건. 자료 행을 지우는 트랜잭션 안에서 함께 만들고, 커밋된 뒤 파일을 지우면 행도 지운다.
 * 파일을 지우지 못하면 행이 남아 FileCleanupService가 다시 시도한다 (DB와 파일이 어긋나지 않게 하는 작업 목록).
 */
@Entity
@Table(name = "file_deletion_task")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FileDeletionTask {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 200)
	private String storageKey;

	@Column(nullable = false)
	private int attemptCount;

	@Column(nullable = false)
	private LocalDateTime createdAt;

	private LocalDateTime lastAttemptedAt;

	public FileDeletionTask(String storageKey, LocalDateTime now) {
		this.storageKey = storageKey;
		this.createdAt = now;
	}

	public void recordFailure(LocalDateTime now) {
		this.attemptCount++;
		this.lastAttemptedAt = now;
	}
}
