package com.ailms.backend.domain.material.entity;

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
 * 올린 학습자료의 정보. 파일 내용은 저장소(FileStorage)의 storageKey 위치에 있다.
 * 나중에 붙을 AI 결과(요약·개념·퀴즈·조각)는 material.id를 외래 키(ON DELETE CASCADE)로 가지는 별도 테이블에 둔다.
 */
@Entity
@Table(name = "material")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Material {

	public static final int TITLE_MAX_LENGTH = 60;
	public static final int FILE_NAME_MAX_LENGTH = 255;
	// spring.servlet.multipart.max-file-size와 같은 값 (프론트 MATERIAL_MAX_SIZE_MB)
	public static final long MAX_FILE_SIZE = 20L * 1024 * 1024;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long userId;

	// 과목에 연결하지 않은 자료는 null
	private Long courseId;

	@Column(nullable = false, length = TITLE_MAX_LENGTH)
	private String title;

	@Column(nullable = false, length = FILE_NAME_MAX_LENGTH)
	private String originalFileName;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private MaterialFileFormat fileFormat;

	@Column(nullable = false, length = 100)
	private String contentType;

	@Column(nullable = false)
	private long fileSize;

	@Column(unique = true, length = 200)
	private String storageKey;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private MaterialStatus status;

	@Column(nullable = false)
	private LocalDateTime createdAt;

	@Column(nullable = false)
	private LocalDateTime updatedAt;

	public static Material upload(Long userId, Long courseId, String title, String originalFileName,
			MaterialFileFormat fileFormat, long fileSize, String storageKey, LocalDateTime now) {
		Material material = new Material();
		material.userId = userId;
		material.courseId = courseId;
		material.title = title;
		material.originalFileName = originalFileName;
		material.fileFormat = fileFormat;
		material.contentType = fileFormat.contentType();
		material.fileSize = fileSize;
		material.storageKey = storageKey;
		material.status = MaterialStatus.UPLOADED;
		material.createdAt = now;
		material.updatedAt = now;
		return material;
	}
}
